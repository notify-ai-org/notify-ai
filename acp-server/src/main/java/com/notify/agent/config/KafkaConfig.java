package com.notify.agent.config;

import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.models.EventCapture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
@Configuration
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    @ManagedConfiguration(key = "kafka.bootstrap-servers")
    private String bootstrapServers;

    @Value("${kafka.poll-timeout-ms}")
    @ManagedConfiguration(key = "kafka.poll-timeout-ms")
    private long pollTimeoutMs;

    @Value("${kafka.key-deserializer}")
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
        return props;
    }

    @Bean
    public ConsumerFactory<String, EventCapture> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties());
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public KafkaConsumer<String, EventCapture> kafkaConsumer() {
        return new KafkaConsumer<>(consumerProperties());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventCapture> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventCapture> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setPollTimeout(pollTimeoutMs);
        return factory;
    }
}
