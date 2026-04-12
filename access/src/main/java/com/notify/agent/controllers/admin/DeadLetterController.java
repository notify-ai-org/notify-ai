package com.notify.agent.controllers.admin;

import com.notify.agent.interfaces.DeadLetterManager;
import com.notify.agent.models.DeadLetterRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dead-letter")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterManager deadLetterManager;

    /**
     * List all pending dead-letter records (paginated).
     *
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20)
     */
    @GetMapping
    public ResponseEntity<Page<DeadLetterRecord>> getDeadLetterRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DeadLetterRecord> records = deadLetterManager.listPending(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(records);
    }

    /**
     * Get a single dead-letter record by ID.
     *
     * @param id the record ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DeadLetterRecord> getDeadLetterRecord(@PathVariable long id) {
        return ResponseEntity.ok(deadLetterManager.get(id));
    }

    /**
     * Search dead-letter records by notification ID.
     *
     * @param notificationId the original notification ID
     * @param page           page number (0-indexed, default 0)
     * @param size           page size (default 20)
     */
    @GetMapping("/search")
    public ResponseEntity<Page<DeadLetterRecord>> searchByNotificationId(
            @RequestParam String notificationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DeadLetterRecord> records = deadLetterManager.searchByNotificationId(
                notificationId, PageRequest.of(page, size));
        return ResponseEntity.ok(records);
    }

    /**
     * Replay a dead-letter record — deserializes the stored job payload and
     * re-dispatches it through the normal processing pipeline.
     *
     * @param id    the record ID
     * @param actor identifier of the person triggering the replay (query param)
     */
    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDeadLetter(
            @PathVariable long id,
            @RequestParam(defaultValue = "admin") String actor) {
        deadLetterManager.replay(id, actor);
        return ResponseEntity.ok(Map.of(
                "status", "replayed",
                "id", id,
                "replayedBy", actor));
    }

    /**
     * Discard a dead-letter record without replaying it.
     *
     * @param id     the record ID
     * @param actor  identifier of the person discarding it
     * @param reason human-readable reason for discarding
     */
    @PostMapping("/{id}/discard")
    public ResponseEntity<Map<String, Object>> discardDeadLetter(
            @PathVariable long id,
            @RequestParam(defaultValue = "admin") String actor,
            @RequestParam(required = false) String reason) {
        deadLetterManager.discard(id, actor, reason);
        return ResponseEntity.ok(Map.of(
                "status", "discarded",
                "id", id,
                "discardedBy", actor));
    }

}
