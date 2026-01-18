package com.example.agent.models;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.example.agent.models.ConnectorProperties.ChannelConfig;

public interface NotificationConnector extends AutoCloseable {

    // Channel name (EMAIL, SMS, PUSH, WEBHOOK)
    String channel();

    // Core send method
    void send(NotificationJob job);

    // Lifecycle
    void bind(ChannelConfig channelConfig);

    void init(AtomicReference<ConnectorMetrics> connectorMetrics);

    void close();

}
