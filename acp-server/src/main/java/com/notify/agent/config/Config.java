package com.notify.agent.config;

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

import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.annotations.ManagedConfiguration.ConfigSource;
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

import com.notify.agent.auth.AgentAuthenticationFilter;
import com.notify.agent.consumers.FactConsumer;
import com.notify.agent.service.LogToMemoryAgentWorker;
import com.notify.agent.util.CentralExecutorRegistry;
import com.notify.agent.util.CentralExecutorRegistry.ExecutorProperties;
import com.notify.agent.util.CentralExecutorRegistry.ExecutorType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableJpaRepositories(basePackages = "com.notify.agent", // your repository package
        entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
@EntityScan(basePackages = "com.notify.agent.models")
@EnableConfigurationProperties({ ExecutorProperties.class, ConnectorProperties.class })
@EnableWebSecurity
@org.springframework.scheduling.annotation.EnableScheduling
public class Config {

    @Value("${server.netty.select-threads:1}")
    private int nettySelectThreads;

    @Value("${server.netty.worker-threads:2}")
    private int nettyWorkerThreads;

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

    /**
     * Constrains Reactor Netty's server-side NIO event loop to a fixed thread count.
     *
     * <p>selectCount: number of NIO boss/accept threads (1 is sufficient for a
     *   single-threaded accept architecture).
     * <p>workerCount: number of NIO I/O worker threads that read/write request data.
     *   Set to 2 by default; tune via {@code server.netty.worker-threads}.
     *
     * <p>Both values default to 1/2 and can be overridden in application properties
     * without a code change:
     * <pre>
     *   server.netty.select-threads=1
     *   server.netty.worker-threads=2
     * </pre>
     */
    @Bean
    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers((NettyServerCustomizer) httpServer -> {
            LoopResources loopResources = LoopResources.create(
                    "nio-req",          // thread name prefix — visible in thread dumps
                    nettySelectThreads, // NIO accept/select thread count
                    nettyWorkerThreads, // NIO I/O worker thread count
                    true                // daemon threads
            );
            return httpServer.runOn(loopResources);
        });
        return factory;
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
    @Value("${spring.datasource.url}")
    @ManagedConfiguration(key = "spring.datasource.url", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbUrl;

    @Value("${spring.datasource.username}")
    @ManagedConfiguration(key = "spring.datasource.username", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbUser;

    @Value("${spring.datasource.password}")
    @ManagedConfiguration(key = "spring.datasource.password", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbUrl);
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);

        // Optional Hikari settings
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
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
                .packages("com.notify.agent.models")
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
        props.put("hibernate.hbm2ddl.auto", "create"); // or validate, create, none
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect"); // change if using another DB
        props.put("hibernate.show_sql", false);
        props.put("hibernate.format_sql", false);
        props.put("hibernate.use_sql_comments", false);
        props.put("hibernate.jdbc.batch_size", 20);
        return props;
    }

    @Bean
    public LogToMemoryAgentWorker logToMemoryAgentWorker(
            com.notify.agent.EventCaptureRepository eventCaptureRepo,
            com.notify.agent.AgentLogRepository agentLogRepo,
            com.notify.agent.NotificationAttemptLogRepository notificationLogRepo,
            com.notify.agent.EventExecutionLogRepository executionLogRepo,
            @Lazy FactConsumer factConsumer) {
        return new LogToMemoryAgentWorker(
                eventCaptureRepo, agentLogRepo, notificationLogRepo, executionLogRepo,
                factConsumer, 50);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public ExecutorService dispatcherExecutor(CentralExecutorRegistry registry) {
        return registry.get(ExecutorType.DISPATCHER);
    }
}
