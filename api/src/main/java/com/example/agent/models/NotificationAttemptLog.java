
package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

import com.example.agent.models.NotificationJob.DispatchMode;
import com.example.agent.models.NotificationJob.NotificationPriority;

import lombok.Data;

@Entity
@Data
public class NotificationAttemptLog {

    @Id
    @GeneratedValue
    private Long id;

    private Instant timestamp;

    private String eventType;
 
    @Lob
    private String error;

    private String result;

    private String channel;

    private DispatchMode dispatchMode;

    private String template;

     /**
     * Target address.
     * Example: email address, phone number, webhook URL.
     */
     private String target;

     private String lastProcessedBy;

     private NotificationPriority priority;
}
