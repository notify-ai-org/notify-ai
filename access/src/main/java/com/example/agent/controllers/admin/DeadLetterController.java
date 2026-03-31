package com.example.agent.controllers.admin;

import com.example.agent.NotificationAttemptLogRepository;
import com.example.agent.NotificationDispatcher;
import com.example.agent.models.DeadLetterRecord;
import com.example.agent.models.NotificationJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dead-letter")
public class DeadLetterController {

    private final NotificationAttemptLogRepository attemptLogRepository;
    private final NotificationDispatcher dispatcher;

    public DeadLetterController(NotificationAttemptLogRepository attemptLogRepository, NotificationDispatcher dispatcher) {
        this.attemptLogRepository = attemptLogRepository;
        this.dispatcher = dispatcher;
    }

    @GetMapping
    public ResponseEntity<List<DeadLetterRecord>> geDeadLetterRecords() {
        return ResponseEntity.ok(attemptLogRepository.findDeadLetterRecords());
    }

    @PostMapping("/dispatch/{id}")
    public ResponseEntity<Map<String, String>> dispatchDeadLetter(@PathVariable String id) {
        DeadLetterRecord record = attemptLogRepository.findDeadLetterRecordById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }

        // Rehydrate a NotificationJob from the DLR
        NotificationJob job = new NotificationJob();
        job.setId(record.getJobId());
        job.setCorrelationId(record.getCorrelationId());
        job.setEventName(record.getEventName());
        job.setDispatchMode(NotificationJob.DispatchMode.RETRY);
        
        // Push manually to processing Queue
        dispatcher.pushJob(job);
        
        // Remove or mark the DLR as dispatched
        attemptLogRepository.deleteDeadLetterRecord(id);

        return ResponseEntity.ok(Map.of("message", "Dead letter record explicitly dispatched", "jobId", job.getId()));
    }
}
