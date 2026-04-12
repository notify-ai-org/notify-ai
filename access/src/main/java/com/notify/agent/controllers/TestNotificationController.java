package com.notify.agent.controllers;

import com.notify.agent.NotificationDispatcher;
import com.notify.agent.models.NotificationJob;
import com.notify.agent.models.NotificationJob.DispatchMode;
import com.notify.agent.models.NotificationJob.NotificationPriority;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import com.notify.agent.consumers.EventConsumer;
import com.notify.agent.models.Event;
import com.notify.agent.models.EventCapture;

@RestController
@RequestMapping("/api/test/notification")
public class TestNotificationController {

        private final NotificationDispatcher notificationDispatcher;
        private final EventConsumer eventConsumer;

        public TestNotificationController(NotificationDispatcher notificationDispatcher,
                        EventConsumer eventConsumer) {
                this.notificationDispatcher = notificationDispatcher;
                this.eventConsumer = eventConsumer;
        }

        @PostMapping
        public ResponseEntity<Map<String, Object>> createDummyJob(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                // Use provided values or defaults for testing
                String channel = (requestParams != null && requestParams.containsKey("channel"))
                                ? requestParams.get("channel").toString()
                                : "WEBHOOK";

                String template = (requestParams != null && requestParams.containsKey("template"))
                                ? requestParams.get("template").toString()
                                : "Dummy Webhook Payload Test: $test_var";

                NotificationJob job = NotificationJob.builder()
                                .id(UUID.randomUUID().toString())
                                .dispatchMode(DispatchMode.EVENT)
                                .channel(channel)
                                .template(template)
                                .source("test-endpoint")
                                .eventName("test_event_" + System.currentTimeMillis())
                                .eventType("test")
                                .priority(NotificationPriority.NORMAL)
                                .build();

                // Push the dummy job to the dispatcher queue directly
                try {
                        // Priority 0 for normal test
                        notificationDispatcher.pushJob(job, 0);

                        return ResponseEntity.accepted().body(Map.of(
                                        "status", "SUCCESS",
                                        "message", "Dispatched dummy notification job",
                                        "jobId", job.getId(),
                                        "channel", job.getChannel()));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }

        @PostMapping("/email")
        public ResponseEntity<Map<String, Object>> createDummyEmailJob(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                String channel = "EMAIL";
                String target = (requestParams != null && requestParams.containsKey("target"))
                                ? requestParams.get("target").toString()
                                : "test@example.com";
                String template = (requestParams != null && requestParams.containsKey("template"))
                                ? requestParams.get("template").toString()
                                : "Dummy Email Payload Test: $test_var";

                try {
                        for (int i = 0; i <= 10; i++) {
                                NotificationJob job = NotificationJob.builder()
                                                .id(UUID.randomUUID().toString())
                                                .dispatchMode(DispatchMode.EVENT)
                                                .channel(channel)
                                                .template(template)
                                                .subjects(new ArrayList<>())
                                                .source("test-endpoint-email")
                                                .eventName("test_event_email_" + System.currentTimeMillis())
                                                .eventType("test")
                                                .priority(NotificationPriority.NORMAL)
                                                .build();
                                notificationDispatcher.pushJob(job, 0);
                        }

                        return ResponseEntity.accepted().body(Map.of(
                                        "status", "SUCCESS",
                                        "message", "Dispatched dummy EMAIL notification jobs"));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }

        @PostMapping("/sms")
        public ResponseEntity<Map<String, Object>> createDummySmsJob(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                String channel = "SMS";
                String target = (requestParams != null && requestParams.containsKey("target"))
                                ? requestParams.get("target").toString()
                                : "+1234567890";
                String template = (requestParams != null && requestParams.containsKey("template"))
                                ? requestParams.get("template").toString()
                                : "Dummy SMS Payload Test: $test_var";

                NotificationJob job = NotificationJob.builder()
                                .id(UUID.randomUUID().toString())
                                .dispatchMode(DispatchMode.EVENT)
                                .channel(channel)
                                .template(template)
                                .source("test-endpoint-sms")
                                .eventName("test_event_sms_" + System.currentTimeMillis())
                                .eventType("test")
                                .priority(NotificationPriority.NORMAL)
                                .build();

                try {
                        notificationDispatcher.pushJob(job, 0);
                        return ResponseEntity.accepted().body(Map.of(
                                        "status", "SUCCESS",
                                        "message", "Dispatched dummy SMS notification job",
                                        "jobId", job.getId(),
                                        "channel", job.getChannel()));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }

        @PostMapping("/events/bulk")
        public ResponseEntity<Map<String, Object>> createDummyBulkEvents(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                int count = (requestParams != null && requestParams.containsKey("count"))
                                ? Integer.parseInt(requestParams.get("count").toString())
                                : 100;

                try {
                        java.util.List<EventCapture> captures = new java.util.ArrayList<>();
                        for (int i = 0; i < count; i++) {
                                EventCapture capture = new EventCapture();
                                capture.setId(UUID.randomUUID().toString());
                                capture.setCorrelationId("test-corr-" + System.currentTimeMillis() + "-" + i);
                                capture.setOccuredAt(java.time.Instant.now());
                                capture.setPayload(Map.of("testKey", "testValue" + i));

                                Event event = new Event();
                                event.setEventType("TEST_EVENT");
                                event.setName("dummy_event_" + i);
                                capture.setEvent(event);

                                captures.add(capture);
                        }

                        eventConsumer.enqueueEventProcessing(captures);

                        return ResponseEntity.accepted().body(Map.of(
                                        "status", "SUCCESS",
                                        "count", captures.size(),
                                        "message", "Dispatched " + count
                                                        + " dummy event captures synchronously for background processing"));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }

        @PostMapping("/events/noise")
        public ResponseEntity<Map<String, Object>> createNoiseEvents(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                int count = (requestParams != null && requestParams.containsKey("count"))
                                ? Integer.parseInt(requestParams.get("count").toString())
                                : 1; // Default to 1 noise event, can handle bulk if count > 1

                try {
                        java.util.List<EventCapture> captures = new java.util.ArrayList<>();
                        for (int i = 0; i < count; i++) {
                                EventCapture capture = new EventCapture();
                                capture.setId(UUID.randomUUID().toString());
                                capture.setCorrelationId("noise-corr-" + System.currentTimeMillis() + "-" + i);
                                capture.setOccuredAt(java.time.Instant.now());
                                // Payload is pure noise/heartbeat to trigger Agent suppression
                                capture.setPayload(Map.of(
                                                "sensorData", "0x0000",
                                                "diagnostics", "Routine heartbeat check. No anomalies detected.",
                                                "logLevel", "TRACE",
                                                "relevance", "IGNORE"));

                                Event event = new Event();
                                event.setEventType("SYSTEM_HEARTBEAT");
                                event.setName("background_noise_ping_" + i);
                                capture.setEvent(event);

                                captures.add(capture);
                        }

                        eventConsumer.processEvents(captures);

                        return ResponseEntity.accepted().body(Map.of(
                                        "status", "SUCCESS",
                                        "count", captures.size(),
                                        "message", "Dispatched " + count
                                                        + " NOISE event captures specifically targeted for Agent suppression"));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }
}
