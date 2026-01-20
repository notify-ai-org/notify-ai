package com.example.agent.models;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.example.agent.models.ConnectorProperties.ChannelConfig;
import com.example.agent.models.subject.Subject;

public interface NotificationConnector extends AutoCloseable {

    // Channel name (EMAIL, SMS, PUSH, WEBHOOK)
    String channel();

    // Core send method
    void send(NotificationJob job,Subject subject);

    // Lifecycle
    void bind(ChannelConfig channelConfig);

    void init(AtomicReference<ConnectorMetrics> connectorMetrics);

    void close();

}
