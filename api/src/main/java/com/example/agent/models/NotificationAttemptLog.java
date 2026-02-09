package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.example.agent.models.NotificationJob.DispatchMode;
import com.example.agent.models.NotificationJob.NotificationPriority;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NotificationAttemptLog extends RawLog {

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
