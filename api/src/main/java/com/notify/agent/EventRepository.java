package com.notify.agent;

import com.notify.agent.models.Event;
import com.notify.agent.models.EventCapture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {
    Optional<Event> findById(String id);

    Optional<Event> findByName(String name);

    List<Event> findByValidated(boolean validated);

    List<Event> findByStatus(Event.EventStatus status);

    List<Event> findByCorrelationId(String correlationId);

    List<Event> findAll();

    /**
     * Get history of event captures for a given event name/type
     * 
     * @param eventName The name/type of the event
     * @return List of EventCapture records for the given event name
     */
    @Query("SELECT ec FROM EventCapture ec JOIN ec.event e WHERE e.name = :eventName ORDER BY ec.timestamp DESC")
    List<EventCapture> getHistory(@Param("eventName") String eventName);
}
