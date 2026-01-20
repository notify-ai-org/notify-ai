package com.example.agent;

import com.example.agent.annotations.Event;
import com.example.agent.sdk.dto.EventCaptureDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import java.time.Instant;

/**
 * Intercepts @Event method execution, sends event details to the Buffer (for
 * acp-server via Dispatcher). Delegates to InvokeManager for before/after
 * callbacks; enriches and sends EventCaptureDto to the Dispatcher. Records metrics.
 * For Kafka scheduled events, see NotifyKafkaListener.
 */
@Aspect
public class EventListener {

    private final Buffer buffer;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;
    private final ObjectMapper mapper;

    public EventListener(Buffer buffer, InvokeManager invokeManager,
                         MetricsManager metricsManager) {
        this.buffer = buffer;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager != null ? metricsManager : new MetricsManager();
        this.mapper = new ObjectMapper();
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
        String payloadJson = toJson(java.util.Map.of("args", pjp.getArgs(), "result", result));

        EventCaptureDto dto = new EventCaptureDto();
        dto.setEventName(eventKey);
        dto.setEventType("USER");
        dto.setEventDescription(ann.description());
        dto.setOccuredAt(Instant.now());
        dto.setPayload(payloadJson);
        dto.setDurationMillis(duration);
        dto.setThreadName(Thread.currentThread().getName());
        dto.setServiceName(pjp.getTarget().getClass().getSimpleName());

        buffer.addEventCapture(dto);
        if (metricsManager != null) metricsManager.recordEventCapture(eventKey, duration);

        return result;
    }

    String toJson(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
}
