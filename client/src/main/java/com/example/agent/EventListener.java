package com.example.agent;

import com.example.agent.annotations.Event;
import com.example.agent.models.EventCapture;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.Instant;

/**
 * Intercepts @Event method execution, sends event details to the Buffer (for
 * acp-server via Dispatcher). Delegates to InvokeManager for before/after
 * callbacks; enriches and sends EventCaptureDto to the Dispatcher. Records
 * metrics.
 * For Kafka scheduled events, see NotifyKafkaListener.
 */
@Aspect
public class EventListener {

    private final Buffer buffer;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;
    private final ObjectMapper mapper;
    private final VocabularyManager vocabularyManager;

    public EventListener(Buffer buffer, InvokeManager invokeManager,
            MetricsManager metricsManager, VocabularyManager vocabularyManager) {
        this.buffer = buffer;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager != null ? metricsManager : new MetricsManager();
        this.mapper = new ObjectMapper();
        this.vocabularyManager = vocabularyManager;
    }

    @Around("@annotation(ev)")
    public Object aroundEvent(ProceedingJoinPoint pjp, Event ev) throws Throwable {
        Event ann = ev;
        String eventKey = ann.key();
        long t0 = System.currentTimeMillis();

        try {
            invokeManager.invokeCallbacksBefore(eventKey, pjp.getArgs());
        } catch (Exception e) {
            // log and continue
        }

        Object result = pjp.proceed();

        try {
            invokeManager.invokeCallbacksAfter(eventKey, pjp.getArgs());
        } catch (Exception e) {
            // log and continue
        }

        long duration = System.currentTimeMillis() - t0;

        EventCapture dto = new EventCapture();
        dto.getEvent().setName(eventKey);
        dto.getEvent().setEventType("USER");
        dto.getEvent().setDescription(ann.description());
        dto.setOccuredAt(Instant.now());
        dto.setPayload(vocabularyManager.toInstanceVocabularyGraph(pjp.getArgs()[0]));
        dto.setDurationMillis(duration);
        dto.setCallStack(Thread.currentThread().getStackTrace());
        dto.setResult(null);
        dto.setException(null);
        dto.setServiceName(pjp.getTarget().getClass().getSimpleName());

        buffer.addEventCapture(dto);
        if (metricsManager != null)
            metricsManager.recordEventCapture(eventKey, duration);

        return result;
    }

    @KafkaListener(topics = "${notify.kafka.topic:notify-scheduled-events}", groupId = "${notify.kafka.group:notify-client-group}")
    public void onScheduledEvent(String raw) {
        try {
            EventCapture dto = mapper.readValue(raw, EventCapture.class);
            if (dto.getOccuredAt() == null)
                dto.setOccuredAt(Instant.now());
            if (dto.getEvent().getEventType() == null || dto.getEvent().getEventType().isEmpty())
                dto.getEvent().setEventType("SCHEDULED");

            String eventKey = dto.getEvent().getName();
            if (eventKey != null && invokeManager != null) {
                try {
                    Object vocab = invokeManager.invokeVocabularySupplier(eventKey, dto.getPayload());
                    if (vocab != null) {
                        dto.setPayload(
                                mapper.writeValueAsString(
                                        java.util.Map.of("vocabulary", vocab, "original", dto.getPayload())));
                    }
                } catch (Exception ignored) {
                }
            }

            buffer.addEventCapture(dto);
            if (metricsManager != null)
                metricsManager.recordEventCapture(dto.getEvent().getName(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
