package com.example.agent;

import com.example.agent.models.JobEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEventLogRepository extends JpaRepository<JobEventLog, Long> {
}