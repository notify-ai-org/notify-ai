package com.notify.agent.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA configuration for the engine module when running standalone.
 * All beans are @ConditionalOnMissingBean so they back off when
 * acp-server's Config.java provides its own definitions.
 *
 * Note: @EnableJpaRepositories and @EntityScan are NOT declared here
 * to avoid duplicate repository scanning when acp-server's Config.java
 * already declares them for the same base packages.
 */
@Configuration
public class JpaConfig {

    // --------------------
    // DataSource: HikariCP
    // --------------------
    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
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
        hikariConfig.setPoolName("HikariCP-Engine");

        return new HikariDataSource(hikariConfig);
    }

    // -------------------------
    // EntityManagerFactory Bean
    // -------------------------
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(dataSource())
                .packages("com.notify.agent.models") // adjust to your entities package
                .persistenceUnit("enginePU")
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

    // --------------------
    // Exception Translation
    // --------------------
    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    // --------------------
    // Hibernate Properties
    // --------------------
    private Map<String, Object> hibernateProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "update"); // Use 'update' or 'none' in production
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect"); // change to desired dialect
        props.put("hibernate.show_sql", false);
        props.put("hibernate.format_sql", false);
        props.put("hibernate.use_sql_comments", false);
        props.put("hibernate.jdbc.batch_size", 20);
        return props;
    }
}
