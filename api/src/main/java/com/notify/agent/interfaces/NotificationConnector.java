package com.notify.agent.interfaces;

import java.util.concurrent.atomic.AtomicReference;

import com.notify.agent.interfaces.ChannelConfig;
import com.notify.agent.models.ConnectorMetrics;
import com.notify.agent.models.NotificationJob;
import com.notify.agent.models.subject.Subject;

public interface NotificationConnector extends AutoCloseable {

    // Channel name (EMAIL, SMS, PUSH, WEBHOOK)
    String channel();

    // Core send method
    void send(NotificationJob job, Subject subject);

    // Lifecycle
    void bind(ChannelConfig channelConfig);

    void init(AtomicReference<ConnectorMetrics> connectorMetrics);

    void close();

}
