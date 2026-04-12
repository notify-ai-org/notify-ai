package com.notify.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notify.agent.models.WorkerSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkerSnapshotRepository
        extends JpaRepository<WorkerSnapshot, String> {

    // Find workers by status
    List<WorkerSnapshot> findByStatus(String status);

    Optional<WorkerSnapshot> findByWorkerId(String workerId);

    // Find stale workers (for reaper)
    List<WorkerSnapshot> findByLastActiveAtBefore(Instant cutoff);

    // Optional: status + staleness
    List<WorkerSnapshot> findByStatusAndLastActiveAtBefore(
            String status,
            Instant cutoff
    );
}
