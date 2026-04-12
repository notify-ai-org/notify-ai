package com.notify.agent;

import com.notify.agent.models.EventCapture;
import com.notify.agent.models.RawLog.ProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventCaptureRepository extends JpaRepository<EventCapture, String> {

    /**
     * Get history of event captures by event name/type
     */
    @Query("SELECT ec FROM EventCapture ec JOIN ec.event e WHERE e.name = :eventName ORDER BY ec.timestamp DESC")
    List<EventCapture> getHistory(@Param("eventName") String eventName);

    /**
     * Find all event captures for a specific event ID
     */
    @Query("SELECT ec FROM EventCapture ec JOIN ec.event e WHERE e.id = :eventId ORDER BY ec.timestamp DESC")
    List<EventCapture> findByEventId(@Param("eventId") String eventId);

    /**
     * Find unprocessed event captures for batch processing.
     */
    List<EventCapture> findByProcessingStatusOrderByTimestampAsc(ProcessingStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE EventCapture l SET l.processingStatus = 'PENDING' WHERE l.processingStatus = 'PROCESSING'")
    int resetStuckProcessingLogs();
}

