package com.notify.agent;

import com.notify.agent.models.NotificationJob;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface NotificationJobRepository
                extends JpaRepository<NotificationJob, String> {
        Optional<NotificationJob> findByEventName(@Param("eventName") String eventName);

        Optional<NotificationJob> findById(@Param("id") String id);
}
