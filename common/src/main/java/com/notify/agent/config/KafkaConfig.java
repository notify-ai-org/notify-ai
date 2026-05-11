package com.notify.agent.config;

import com.notify.agent.annotations.ManagedConfiguration;

import lombok.Getter;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Kafka configuration for consumer and producer clients.
 *
 * <p>Used by {@code acp-server}, {@code engine}, and {@code client} via the
 * {@code vocabulary-agent-common} dependency. SASL/SSL security properties
 * are applied conditionally based on {@code kafka.security.protocol}.</p>
 */
@Configuration
@Getter
public class KafkaConfig {

    // -------------------------------------------------------------------------
    // Consumer configuration
    // -------------------------------------------------------------------------

    @Value("${kafka.bootstrap-servers:pkc-41p56.asia-south1.gcp.confluent.cloud:9092}")
    @ManagedConfiguration(key = "kafka.bootstrap-servers")
    private String bootstrapServers;

    @Value("${kafka.poll-timeout-ms}")
    @ManagedConfiguration(key = "kafka.poll-timeout-ms")
    private long pollTimeoutMs;

    @Value("${kafka.key-deserializer:org.apache.kafka.common.serialization.StringDeserializer}")
    @ManagedConfiguration(key = "kafka.key-deserializer")
    private String keyDeserializer;

    @Value("${kafka.value-deserializer}")
    @ManagedConfiguration(key = "kafka.value-deserializer")
    private String valueDeserializer;

    @Value("${kafka.client-id}")
    @ManagedConfiguration(key = "kafka.client-id")
    private String clientId;

    @Value("${kafka.fetch-max-wait-ms}")
    @ManagedConfiguration(key = "kafka.fetch-max-wait-ms")
    private int fetchMaxWaitMs;

    @Value("${kafka.fetch-min-bytes}")
    @ManagedConfiguration(key = "kafka.fetch-min-bytes")
    private int fetchMinBytes;

    @Value("${kafka.fetch-max-bytes}")
    @ManagedConfiguration(key = "kafka.fetch-max-bytes")
    private int fetchMaxBytes;

    @Value("${kafka.max-partition-fetch-bytes}")
    @ManagedConfiguration(key = "kafka.max-partition-fetch-bytes")
    private int maxPartitionFetchBytes;

    @Value("${kafka.max-message-bytes}")
    @ManagedConfiguration(key = "kafka.max-message-bytes")
    private int maxMessageBytes;

    @Value("${kafka.isolation-level}")
    @ManagedConfiguration(key = "kafka.isolation-level")
    private String isolationLevel;

    @Value("${kafka.enable-auto-commit}")
    @ManagedConfiguration(key = "kafka.enable-auto-commit")
    private boolean enableAutoCommit;

    @Value("${kafka.heartbeat-interval-ms}")
    @ManagedConfiguration(key = "kafka.heartbeat-interval-ms")
    private int heartbeatIntervalMs;

    @Value("${kafka.session-timeout-ms}")
    @ManagedConfiguration(key = "kafka.session-timeout-ms")
    private int sessionTimeoutMs;

    @Value("${kafka.auto-offset-reset}")
    @ManagedConfiguration(key = "kafka.auto-offset-reset")
    private String autoOffsetReset;

    @Value("${kafka.group-instance-id}")
    @ManagedConfiguration(key = "kafka.group-instance-id")
    private String groupInstanceId;

    @Value("${kafka.max-poll-interval-ms}")
    @ManagedConfiguration(key = "kafka.max-poll-interval-ms")
    private int maxPollIntervalMs;

    @Value("${kafka.max-poll-records}")
    @ManagedConfiguration(key = "kafka.max-poll-records")
    private int maxPollRecords;

    // -------------------------------------------------------------------------
    // Producer configuration
    // -------------------------------------------------------------------------

    @Value("${kafka.producer.key-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    @ManagedConfiguration(key = "kafka.producer.key-serializer")
    private String keySerializer;

    @Value("${kafka.producer.value-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    @ManagedConfiguration(key = "kafka.producer.value-serializer")
    private String valueSerializer;

    @Value("${kafka.producer.acks:all}")
    @ManagedConfiguration(key = "kafka.producer.acks")
    private String acks;

    @Value("${kafka.producer.retries:3}")
    @ManagedConfiguration(key = "kafka.producer.retries")
    private int retries;

    @Value("${kafka.producer.max-request-size:1048576}")
    @ManagedConfiguration(key = "kafka.producer.max-request-size")
    private int maxRequestSize;

    @Value("${kafka.producer.batch-size:16384}")
    @ManagedConfiguration(key = "kafka.producer.batch-size")
    private int batchSize;

    @Value("${kafka.producer.linger-ms:5}")
    @ManagedConfiguration(key = "kafka.producer.linger-ms")
    private int lingerMs;

    @Value("${kafka.producer.compression-type:snappy}")
    @ManagedConfiguration(key = "kafka.producer.compression-type")
    private String compressionType;

    @Value("${kafka.producer.buffer-memory:33554432}")
    @ManagedConfiguration(key = "kafka.producer.buffer-memory")
    private long bufferMemory;

    @Value("${kafka.producer.request-timeout-ms:30000}")
    @ManagedConfiguration(key = "kafka.producer.request-timeout-ms")
    private int requestTimeoutMs;

    @Value("${kafka.producer.delivery-timeout-ms:120000}")
    @ManagedConfiguration(key = "kafka.producer.delivery-timeout-ms")
    private int deliveryTimeoutMs;

    @Value("${kafka.producer.enable-idempotence:true}")
    @ManagedConfiguration(key = "kafka.producer.enable-idempotence")
    private boolean enableIdempotence;

    @Value("${kafka.producer.transactional-id:}")
    @ManagedConfiguration(key = "kafka.producer.transactional-id")
    private String transactionalId;

    // -------------------------------------------------------------------------
    // Security configuration (SASL / SSL)
    // -------------------------------------------------------------------------

    /** PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL */
    @Value("${kafka.security.protocol:SASL_SSL}")
    @ManagedConfiguration(key = "kafka.security.protocol")
    private String securityProtocol;

    /** PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512 | OAUTHBEARER | GSSAPI */
    @Value("${kafka.security.sasl-mechanism:PLAIN}")
    @ManagedConfiguration(key = "kafka.security.sasl-mechanism")
    private String saslMechanism;

    /** Full JAAS config string — kept blank to disable SASL */
    @Value("${kafka.security.sasl-jaas-config:}")
    @ManagedConfiguration(key = "kafka.security.sasl-jaas-config")
    private String saslJaasConfig;

    @Value("${kafka.security.ssl.truststore-location:}")
    @ManagedConfiguration(key = "kafka.security.ssl.truststore-location")
    private String sslTruststoreLocation;

    @Value("${kafka.security.ssl.truststore-password:}")
    @ManagedConfiguration(key = "kafka.security.ssl.truststore-password")
    private String sslTruststorePassword;

    /** Leave blank to disable mutual TLS (client certificate auth via SSL) */
    @Value("${kafka.security.ssl.keystore-location:}")
    @ManagedConfiguration(key = "kafka.security.ssl.keystore-location")
    private String sslKeystoreLocation;

    @Value("${kafka.security.ssl.keystore-password:}")
    @ManagedConfiguration(key = "kafka.security.ssl.keystore-password")
    private String sslKeystorePassword;

    @Value("${kafka.security.ssl.key-password:}")
    @ManagedConfiguration(key = "kafka.security.ssl.key-password")
    private String sslKeyPassword;

    /** JKS | PKCS12 */
    @Value("${kafka.security.ssl.truststore-type:JKS}")
    @ManagedConfiguration(key = "kafka.security.ssl.truststore-type")
    private String sslTruststoreType;

    @Value("${kafka.security.ssl.keystore-type:JKS}")
    @ManagedConfiguration(key = "kafka.security.ssl.keystore-type")
    private String sslKeystoreType;

    // -------------------------------------------------------------------------
    // Consumer beans
    // -------------------------------------------------------------------------

    @Bean
    public Map<String, Object> consumerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchMaxBytes);
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, maxPartitionFetchBytes);
        props.put("max.message.bytes", maxMessageBytes);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, groupInstanceId);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        applySecurityProperties(props);
        return props;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public KafkaConsumer<String, Object> kafkaConsumer() {
        return new KafkaConsumer<>(consumerProperties());
    }

    // -------------------------------------------------------------------------
    // Producer beans
    // -------------------------------------------------------------------------

    @Bean
    public Map<String, Object> producerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);
        if (transactionalId != null && !transactionalId.isBlank()) {
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        }
        applySecurityProperties(props);
        return props;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public KafkaProducer<String, Object> kafkaProducer() {
        return new KafkaProducer<>(producerProperties());
    }

    // -------------------------------------------------------------------------
    // Security helper
    // -------------------------------------------------------------------------

    private void applySecurityProperties(Map<String, Object> props) {
        if (securityProtocol == null || securityProtocol.isBlank()
                || "PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            return; // no-op for local/dev
        }

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        // SASL
        if (!saslMechanism.isBlank()) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
        if (!saslJaasConfig.isBlank()) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        }

        // SSL / TLS
        boolean hasSsl = securityProtocol.toUpperCase().contains("SSL");
        if (hasSsl) {
            if (!sslTruststoreLocation.isBlank()) {
                props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTruststoreLocation);
                props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslTruststorePassword);
                props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
            }
            // Optional: mutual TLS (client cert)
            if (!sslKeystoreLocation.isBlank()) {
                props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocation);
                props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePassword);
                props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslKeyPassword);
                props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, sslKeystoreType);
            }
        }
    }
}
