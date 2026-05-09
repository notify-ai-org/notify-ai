package com.notify.agent.consumers;

import com.notify.agent.config.KafkaConfig;
import com.notify.agent.models.EventCapture;
import com.google.adk.events.Event;
import com.notify.agent.AgentContextHolder;
import com.notify.agent.ClientRepository;
import com.notify.agent.models.AgentContext;
import com.notify.agent.service.IdempotencyService;
import com.notify.agent.service.IdempotencyService.AcquireResult;

import io.reactivex.rxjava3.core.Flowable;

import com.notify.agent.service.JwtService;
import com.notify.agent.service.SessionService;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
abstract class KafkaNotifyConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaNotifyConsumer.class);

    private final KafkaConfig kafkaConfig;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final IdempotencyService idempotencyService;
    private final ClientRepository clientRepository;

    private static final String TOPIC = "notify-v1-events";

    // --- Runtime State Members ---
    private final List<KafkaConsumer<String, EventCapture>> consumers = new CopyOnWriteArrayList<>();
    private ExecutorService executorService;
    private volatile boolean running = false;
    private final Set<String> committedPartitions = ConcurrentHashMap.newKeySet();
    protected AtomicLong messageCount = new AtomicLong(0);

    public KafkaNotifyConsumer(KafkaConfig kafkaConfig, ExecutorService executorService,
            JwtService jwtService, SessionService sessionService,
            IdempotencyService idempotencyService, ClientRepository clientRepository) {
        this.kafkaConfig = kafkaConfig;
        this.executorService = executorService;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.idempotencyService = idempotencyService;
        this.clientRepository = clientRepository;
    }

    private KafkaConsumer<String, EventCapture> createKafkaConsumer() {
        return kafkaConfig.kafkaConsumer();
    }

    public void validateConfiguration() {
        if (kafkaConfig.getBootstrapServers() == null || kafkaConfig.getBootstrapServers().isEmpty()) {
            log.warn("Kafka bootstrap servers are not configured. Consumer will not start.");
        }
    }

    public void start() {
        try {
            validateConfiguration();

            // Determine number of partitions for the topic
            int numPartitions = 1;
            try (KafkaConsumer<String, EventCapture> tempConsumer = createKafkaConsumer()) {
                List<PartitionInfo> partitions = tempConsumer.partitionsFor(TOPIC);
                if (partitions != null && !partitions.isEmpty()) {
                    numPartitions = partitions.size();
                    log.info("Found {} partitions for topic {}", numPartitions, TOPIC);
                } else {
                    log.warn("No partitions found for topic {}, defaulting to 1 consumer thread", TOPIC);
                }
            } catch (Exception e) {
                log.warn("Failed to determine partitions for topic {}, defaulting to 1 consumer thread", TOPIC, e);
            }

            this.running = true;
            this.executorService = Executors.newFixedThreadPool(numPartitions, r -> {
                Thread thread = new Thread(r);
                thread.setName("kafka-consumer-" + TOPIC + "-thread");
                thread.setDaemon(false);
                thread.setUncaughtExceptionHandler((t, e) -> log
                        .error("Uncaught exception in Kafka consumer thread {}: {}", t.getName(), e.getMessage(), e));
                return thread;
            });

            // Create a consumer per partition
            for (int i = 0; i < numPartitions; i++) {
                spawnConsumer();
            }

            log.info("Kafka consumer started with {} threads for topic {}", numPartitions, TOPIC);
        } catch (Exception e) {
            log.error("Failed to start Kafka consumer", e);
            stop();
        }
    }

    private void spawnConsumer() {
        KafkaConsumer<String, EventCapture> consumer = createKafkaConsumer();
        consumers.add(consumer);

        // Subscribe to the topic and let Kafka's assignor handle partition distribution
        consumer.subscribe(Collections.singletonList(TOPIC), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                log.info("Revoked partitions: {}", partitions);
                try {
                    // Synchronous fallback on rebalance
                    consumer.commitSync();
                } catch (Exception e) {
                    log.error("Failed to commit sync during partition revocation", e);
                }
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                log.info("Assigned partitions: {}", partitions);
            }

            @Override
            public void onPartitionsLost(Collection<TopicPartition> partitions) {
                log.info("Lost partitions: {}", partitions);
            }
        });

        log.info("Consumer thread spawned and subscribed to topic {}", TOPIC);
        executorService.submit(() -> consumeMessages(consumer));
    }

    private void consumeMessages(KafkaConsumer<String, EventCapture> consumer) {
        log.info("Kafka consumer thread started - polling for messages");
        try {
            while (running) {
                try {
                    ConsumerRecords<String, EventCapture> records = consumer.poll(Duration.ofMillis(100));
                    if (!records.isEmpty()) {
                        processRecords(consumer, records);
                    }
                } catch (OffsetOutOfRangeException | NoOffsetForPartitionException e) {
                    log.warn("Invalid or no offset found, and auto.reset.policy unset, using latest");
                    consumer.seekToEnd(e.partitions());
                    consumer.commitSync();
                } catch (Exception e) {
                    if (e instanceof org.apache.kafka.common.errors.WakeupException) {
                        log.info("Kafka consumer woke up");
                        break;
                    }
                    log.error("Error polling Kafka", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Fatal error in consumer thread, terminating thread.", t);
            if (running) {
                log.info("Respawning consumer thread...");
                consumers.remove(consumer);
                try {
                    consumer.close();
                } catch (Exception ignore) {
                }

                // Automatically respawn consumer to maintain thread pool capacity
                spawnConsumer();
            }
        } finally {
            try {
                if (running) {
                    // We only do this if we aren't already closed by a crash
                    consumer.commitSync();
                }
            } catch (Exception e) {
                log.warn("Failed to commit synchronously on shutdown/exit", e);
            }
        }
        log.info("Kafka consumer thread stopped");
    }

    private void processRecords(KafkaConsumer<String, EventCapture> consumer,
            ConsumerRecords<String, EventCapture> records) throws Exception {
        log.info("Processing {} records from Kafka", records.count());

        Map<String, List<EventCapture>> groupedByTenant = new HashMap<>();
        // Store the first bearer token seen per tenantId (from Kafka record headers)
        Map<String, String> tenantTokens = new HashMap<>();

        for (ConsumerRecord<String, EventCapture> record : records) {
            String messageKey = record.key();
            EventCapture messageValue = record.value();
            String topic = record.topic();
            int partition = record.partition();
            long offset = record.offset();

            log.info("Record - Topic: {}, Partition: {}, Offset: {}, Key: {}, Value: {}",
                    topic, partition, offset, messageKey, messageValue);

            messageCount.incrementAndGet();

            if (messageKey != null && messageValue != null) {
                String[] keyParts = messageKey.split(":");
                String tenantId = keyParts.length > 0 ? keyParts[0] : "unknown";
                groupedByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(messageValue);

                // Extract bearer token from Kafka headers (first token per tenant wins)
                if (!tenantTokens.containsKey(tenantId)) {
                    org.apache.kafka.common.header.Header authHeader = record.headers().lastHeader("Authorization");
                    if (authHeader == null)
                        authHeader = record.headers().lastHeader("X-Auth-Token");
                    if (authHeader != null && authHeader.value() != null) {
                        tenantTokens.put(tenantId,
                                new String(authHeader.value(), StandardCharsets.UTF_8));
                    }
                }
            }
        }

        for (Map.Entry<String, List<EventCapture>> entry : groupedByTenant.entrySet()) {
            String tenantId = entry.getKey();
            List<EventCapture> captures = entry.getValue();

            // --- JWT validation + client check ---
            String rawBearerToken = tenantTokens.get(tenantId);
            JwtService.JwtClaims claims = jwtService.validateAndExtract(rawBearerToken);

            String clientId;
            String userId;
            List<String> resolvedScopes;
            String resolvedRawToken;

            if (claims != null) {
                // JWT present and valid — verify clientId matches the tenantId from message key
                if (claims.getClientId() == null || !claims.getClientId().equals(tenantId)) {
                    log.warn("Skipping tenant group '{}': JWT clientId '{}' does not match message tenantId",
                            tenantId, claims.getClientId());
                    continue;
                }
                clientId = claims.getClientId();
                userId = claims.getUserId() != null ? claims.getUserId() : "kafka-consumer";
                resolvedScopes = claims.getScopes() != null ? claims.getScopes() : List.of("agent:invoke");
                resolvedRawToken = claims.getRawToken();
            } else {
                // No JWT header or invalid token — fall back to ClientRepository-only check
                // (used for internal/system-generated messages without a user token)
                if (rawBearerToken != null) {
                    log.warn("Skipping tenant group '{}': JWT header present but invalid/expired", tenantId);
                    continue;
                }
                log.debug("No JWT header for tenant '{}'; applying ClientRepository-only validation", tenantId);
                clientId = tenantId;
                userId = "kafka-consumer";
                resolvedScopes = List.of("agent:invoke");
                resolvedRawToken = null;
            }

            // ClientRepository check — always applied regardless of JWT presence
            com.notify.agent.models.ClientEntity clientEntity = clientRepository.findByClientId(clientId).orElse(null);
            if (clientEntity == null) {
                log.warn("Skipping tenant group '{}': no registered client found", tenantId);
                continue;
            }
            if (clientEntity.isExpired()) {
                log.warn("Skipping tenant group '{}': client has expired", tenantId);
                continue;
            }

            // --- Context + session creation ---
            String sessionId = "kafka-" + tenantId;
            com.notify.agent.models.AgentSessionEntity sessionEntity = sessionService
                    .findBySessionIdAndClientId(sessionId, clientId)
                    .orElseGet(() -> sessionService.createOrGet(
                            sessionId, clientId, userId,
                            String.join(" ", resolvedScopes)));

            AgentContext ctx = new AgentContext();
            ctx.setSession(sessionEntity.toSession());
            ctx.setSource(clientId);
            ctx.setTenantId(tenantId);
            ctx.setAuthToken(resolvedRawToken);
            ctx.setRoles(new java.util.HashSet<>(resolvedScopes));
            AgentContextHolder.setContext(ctx);

            // --- Idempotency per message ---
            // Filter out any EventCaptures whose correlationId has already been COMPLETED.
            // Remaining captures are processed and their keys marked COMPLETED after
            // enqueue.
            List<EventCapture> pending = new ArrayList<>();
            List<String> acquiredKeys = new ArrayList<>();

            for (EventCapture capture : captures) {
                String correlationId = capture.getCorrelationId();
                if (correlationId == null || correlationId.isBlank()) {
                    // No idempotency key — always process
                    pending.add(capture);
                    acquiredKeys.add(null);
                    continue;
                }
                String[] redisKeyOut = new String[1];
                AcquireResult result = idempotencyService.acquireLock(tenantId, correlationId, redisKeyOut);
                switch (result) {
                    case ACQUIRED -> {
                        pending.add(capture);
                        acquiredKeys.add(redisKeyOut[0]);
                    }
                    case ALREADY_COMPLETED ->
                        log.info("Skipping duplicate event (correlationId={}): already completed", correlationId);
                    case STILL_PROCESSING ->
                        log.warn("Skipping event (correlationId={}): concurrent processing in-flight", correlationId);
                    case INTERRUPTED -> {
                        log.warn("Idempotency check interrupted for correlationId={}; processing anyway",
                                correlationId);
                        pending.add(capture);
                        acquiredKeys.add(redisKeyOut[0]);
                    }
                }
            }

            try {
                if (!pending.isEmpty()) {
                    enqueueEventProcessing(pending)
                            .doOnComplete(() -> {
                                // Mark each acquired idempotency key as COMPLETED
                                for (String key : acquiredKeys) {
                                    idempotencyService.markCompleted(key);
                                }
                            })
                            .subscribe(
                                    event -> {
                                        /* Terminal elements dropped or logged */ },
                                    error -> {
                                        log.error("Event processing pipeline failed: " + error.getMessage(), error);
                                        for (String key : acquiredKeys) {
                                            idempotencyService.releaseLock(key);
                                        }
                                    });
                }
            } catch (Exception ex) {
                log.error("Error enqueuing events for tenant '{}'; releasing idempotency locks", tenantId, ex);
                for (String key : acquiredKeys) {
                    idempotencyService.releaseLock(key);
                }
            } finally {
                AgentContextHolder.clear();
            }
        }

        consumer.commitAsync((offsets, exception) -> {
            if (exception != null) {
                log.error("Failed to commit offsets asynchronously", exception);
            } else if (offsets != null) {
                offsets.forEach((partition, metadata) -> {
                    log.info("Successfully committed offset {} for partition {}", metadata.offset(),
                            partition.partition());
                });
            }
        });
    }

    protected abstract Flowable<com.google.adk.events.Event> enqueueEventProcessing(List<EventCapture> captures);

    @PreDestroy
    public void stop() {
        try {
            running = false;
            for (KafkaConsumer<String, EventCapture> consumer : consumers) {
                if (consumer != null) {
                    consumer.wakeup();
                }
            }
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            for (KafkaConsumer<String, EventCapture> consumer : consumers) {
                if (consumer != null) {
                    try {
                        // Attempt synchronous commit before close
                        consumer.commitSync();
                    } catch (Exception e) {
                        log.warn("Failed to commit on stop", e);
                    }
                    try {
                        consumer.close(Duration.ofSeconds(10));
                    } catch (Exception e) {
                        log.warn("Error closing consumer", e);
                    }
                }
            }
            consumers.clear();
            log.info("Kafka consumers stopped gracefully");
        } catch (Exception e) {
            log.error("Error stopping Kafka consumers", e);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Set<String> getCommittedPartitions() {
        return new HashSet<>(committedPartitions);
    }
}
