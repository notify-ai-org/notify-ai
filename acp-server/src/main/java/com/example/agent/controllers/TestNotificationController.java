package com.example.agent.controllers;

import com.example.agent.NotificationDispatcher;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.NotificationJob.DispatchMode;
import com.example.agent.models.NotificationJob.NotificationPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test/notification")
@RequiredArgsConstructor
public class TestNotificationController {

        private final NotificationDispatcher notificationDispatcher;

        @PostMapping
        public ResponseEntity<Map<String, Object>> createDummyJob(
                        @RequestBody(required = false) Map<String, Object> requestParams) {

                // Use provided values or defaults for testing
                String channel = (requestParams != null && requestParams.containsKey("channel"))
                                ? requestParams.get("channel").toString()
                                : "WEBHOOK";

                String target = (requestParams != null && requestParams.containsKey("target"))
                                ? requestParams.get("target").toString()
                                : "http://localhost:8080/dummy";

                String template = (requestParams != null && requestParams.containsKey("template"))
                                ? requestParams.get("template").toString()
                                : "Dummy Webhook Payload Test: $test_var";

                NotificationJob job = NotificationJob.builder()
                                .id(UUID.randomUUID().toString())
                                .dispatchMode(DispatchMode.EVENT)
                                .channel(channel)
                                .target(target)
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
                                        "channel", job.getChannel(),
                                        "target", job.getTarget()));
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
                        for (int i = 0; i <= 1000; i++) {
                                NotificationJob job = NotificationJob.builder()
                                                .id(UUID.randomUUID().toString())
                                                .dispatchMode(DispatchMode.EVENT)
                                                .channel(channel)
                                                .target(target)
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
                                .target(target)
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
                                        "channel", job.getChannel(),
                                        "target", job.getTarget()));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "ERROR",
                                        "message", e.getMessage()));
                }
        }
}
