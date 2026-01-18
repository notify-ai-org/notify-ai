package com.example.agent;

import com.example.agent.models.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    List<MessageTemplate> findByChannel(String channel);
    List<MessageTemplate> findByEventType(String eventType);
}

