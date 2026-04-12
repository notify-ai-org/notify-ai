package com.notify.agent.models;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.notify.agent.models.NotificationJob.DispatchMode;
import com.notify.agent.models.NotificationJob.NotificationPriority;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NotificationAttemptLog extends RawLog {

    private String eventType;

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
