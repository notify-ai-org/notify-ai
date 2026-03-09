package com.example.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.example.agent.annotations.ManagedConfiguration;
import com.example.agent.annotations.ManagedConfiguration.ConfigSource;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.example.agent.auth.AgentAuthenticationFilter;
import com.example.agent.consumers.FactConsumer;
import com.example.agent.models.RawLog;
import com.example.agent.service.LogToMemoryAgentWorker;
import com.example.agent.util.CentralExecutorRegistry.ExecutorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableJpaRepositories(basePackages = "com.example.agent", // your repository package
        entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
@EntityScan(basePackages = "com.example.agent.models")
@EnableConfigurationProperties({ ExecutorProperties.class, ConnectorProperties.class })
@EnableWebSecurity
public class Config {

    @Value("${redis.host}")
    @ManagedConfiguration(key = "redis.host", source = ConfigSource.CONFIG_MAP)
    private String redisHost;

    @Value("${redis.port}")
    @ManagedConfiguration(key = "redis.port", source = ConfigSource.CONFIG_MAP)
    private int redisPort;

    @Value("${redis.username}")
    @ManagedConfiguration(key = "redis.username", source = ConfigSource.CONFIG_MAP)
    private String redisUsername;

    @Value("${redis.password}")
    @ManagedConfiguration(key = "redis.password", source = ConfigSource.CONFIG_MAP)
    private String redisPassword;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    @ManagedConfiguration(key = "openai.api.base-url")
    private String baseUrl;

    @Value("${openai.api.key}")
    @ManagedConfiguration(key = "openai.api.key")
    private String apiKey;

    @Value("${openai.api.timeout:30s}")
    @ManagedConfiguration(key = "openai.api.timeout")
    private Duration timeout;

    @Value("${openai.api.connection-timeout:10s}")
    @ManagedConfiguration(key = "openai.api.connection-timeout")
    private Duration connectionTimeout;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectionTimeout.toMillis())
                .responseTimeout(timeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout.toSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public RedisClient redisClient() {
        String redisUri = String.format("redis://%s:%s@%s:%d", redisUsername, redisPassword, redisHost, redisPort);
        return RedisClient.create(redisUri);
    }

    @Bean
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    public io.lettuce.core.api.reactive.RedisReactiveCommands<String, byte[]> redisReactiveByteCommands(
            RedisClient redisClient) {
        io.lettuce.core.codec.RedisCodec<String, byte[]> codec = io.lettuce.core.codec.RedisCodec.of(
                io.lettuce.core.codec.StringCodec.UTF8,
                io.lettuce.core.codec.ByteArrayCodec.INSTANCE);
        return redisClient.connect(codec).reactive();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AgentAuthenticationFilter agentAuthFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        // .addFilterBefore(agentAuthFilter,
        // UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // --------------------
    // DataSource: HikariCP
    // --------------------
    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/vocabdb}")
    @ManagedConfiguration(key = "spring.datasource.url", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbUrl;

    @Value("${spring.datasource.username:postgres}")
    @ManagedConfiguration(key = "spring.datasource.username", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbUser;

    @Value("${spring.datasource.password:postgres}")
    @ManagedConfiguration(key = "spring.datasource.password", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbUrl);
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);

        // Optional Hikari settings
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setConnectionTimeout(20000);
        hikariConfig.setPoolName("HikariCP");

        return new HikariDataSource(hikariConfig);
    }

    // -------------------------
    // EntityManagerFactory Bean
    // -------------------------
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(dataSource())
                .packages("com.example.agent.models")
                .persistenceUnit("defaultPU")
                .properties(hibernateProperties())
                .build();
    }

    // ------------------------
    // Transaction Manager Bean
    // ------------------------
    @Bean(name = "transactionManager")
    public JpaTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // ------------------------
    // Exception Translation
    // ------------------------
    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    // --------------------
    // Hibernate Properties
    // --------------------
    private Map<String, Object> hibernateProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "update"); // or validate, create, none
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"); // change if using another DB
        props.put("hibernate.show_sql", false);
        props.put("hibernate.format_sql", false);
        props.put("hibernate.use_sql_comments", false);
        props.put("hibernate.jdbc.batch_size", 20);
        return props;
    }

    @Bean
    public BlockingQueue<RawLog> rawLogQueue() {
        return new LinkedBlockingQueue<>(10000);
    }

    @Bean
    public LogToMemoryAgentWorker logToMemoryAgentWorker(
            BlockingQueue<RawLog> queue,
            @Lazy FactConsumer factConsumer) {
        // Default values: batch size 50, flush delay 5 seconds
        return new LogToMemoryAgentWorker(queue, factConsumer, 50, 5000);
    }
}
