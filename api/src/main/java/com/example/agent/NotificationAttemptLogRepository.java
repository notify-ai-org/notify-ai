package com.example.agent;

import com.example.agent.models.NotificationAttemptLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationAttemptLogRepository
        extends JpaRepository<NotificationAttemptLog, Long> {}
