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
}
