package com.example.agent;

import com.example.agent.models.EventCapture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventCaptureRepository extends JpaRepository<EventCapture, String> {

    /**
     * Get history of event captures by event name/type
     * 
     * @param eventName The name/type of the event
     * @return List of EventCapture records for the given event name
     */
    @Query("SELECT ec FROM EventCapture ec JOIN ec.event e WHERE e.name = :eventName ORDER BY ec.timestamp DESC")
    List<EventCapture> getHistory(@Param("eventName") String eventName);

    /**
     * Find all event captures for a specific event ID
     */
    @Query("SELECT ec FROM EventCapture ec JOIN ec.event e WHERE e.id = :eventId ORDER BY ec.timestamp DESC")
    List<EventCapture> findByEventId(@Param("eventId") String eventId);

}
