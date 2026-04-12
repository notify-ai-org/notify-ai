package com.notify.agent.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {

    private Map<String, ChannelConfigImpl> channel = new HashMap<>();
    private Reload reload = new Reload();

    @Data
    public static class ChannelConfigImpl implements com.notify.agent.interfaces.ChannelConfig {
        private String clazz;
        private int instances = 1;
        private long delay;
        private int maxAttempts;
        private int backOffMultiplier;
    }

    @Data
    public static class Reload {
        private boolean enabled = true;
        private long pollMs = 2000;
    }
}