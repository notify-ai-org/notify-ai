package com.example.agent;

import com.example.agent.models.EventSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventScheduleRepository extends JpaRepository<EventSchedule, String> {
    List<EventSchedule> findByValidated(boolean validated);
    List<EventSchedule> findByEventName(String eventName);
}
