package com.example.agent;

import com.example.agent.sdk.dto.EventCaptureDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.annotation.KafkaListener;

import java.time.Instant;

/**
 * Subscribes to a Kafka topic for scheduled events from acp-server. When
 * notify.kafka.enabled=true and spring.kafka.bootstrap-servers is set, this
 * listener consumes JSON matching EventCaptureDto, optionally enriches with
 * vocabulary via InvokeManager, and adds to the Buffer for the Dispatcher.
 * Registered as a @Bean by NotifyAutoConfiguration when notify.kafka.enabled=true.
 */
public class NotifyKafkaListener {

    private final Buffer buffer;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotifyKafkaListener(Buffer buffer, InvokeManager invokeManager, MetricsManager metricsManager) {
        this.buffer = buffer;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager != null ? metricsManager : new MetricsManager();
    }

    @KafkaListener(
        topics = "${notify.kafka.topic:notify-scheduled-events}",
        groupId = "${notify.kafka.group:notify-client-group}"
    )
    public void onScheduledEvent(String raw) {
        try {
            EventCaptureDto dto = mapper.readValue(raw, EventCaptureDto.class);
            if (dto.getOccuredAt() == null) dto.setOccuredAt(Instant.now());
            if (dto.getEventType() == null || dto.getEventType().isEmpty()) dto.setEventType("SCHEDULED");

            String eventKey = dto.getEventName();
            if (eventKey != null && invokeManager != null) {
                try {
                    Object vocab = invokeManager.invokeVocabularySupplier(eventKey, dto.getPayload());
                    if (vocab != null) {
                        dto.setPayload(mapper.writeValueAsString(java.util.Map.of("vocabulary", vocab, "original", dto.getPayload())));
                    }
                } catch (Exception ignored) {}
            }

            buffer.addEventCapture(dto);
            if (metricsManager != null) metricsManager.recordEventCapture(dto.getEventName(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
