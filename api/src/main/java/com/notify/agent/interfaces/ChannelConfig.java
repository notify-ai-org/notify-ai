package com.notify.agent.interfaces;

public interface ChannelConfig {
    String getClazz();

    int getInstances();

    long getDelay();

    int getMaxAttempts();

    int getBackOffMultiplier();
}
