package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "worker_snapshot", indexes = {
        @Index(name = "idx_worker_snapshot_worker_id", columnList = "worker_id"),
        @Index(name = "idx_worker_snapshot_status", columnList = "status"),
        @Index(name = "idx_worker_snapshot_last_active", columnList = "last_active_at")
})
public class WorkerSnapshot {

    @Id
    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "job_id")
    private String jobId;

    /** JPA requires a no-args constructor */
    protected WorkerSnapshot() {
    }

    public WorkerSnapshot(
            String workerId,
            Instant lastActiveAt,
            String status,
            String jobId) {
        this.workerId = workerId;
        this.lastActiveAt = lastActiveAt;
        this.status = status;
        this.jobId = jobId;
    }

    // getters & setters

    public String getWorkerId() {
        return workerId;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
