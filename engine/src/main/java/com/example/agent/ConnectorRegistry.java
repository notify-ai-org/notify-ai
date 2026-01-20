package com.example.agent;

import java.util.*;

import com.example.agent.models.ConnectorProperties;
import com.example.agent.models.NotificationConnector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final ConnectorProperties connectorProperties;
    private final AutowireCapableBeanFactory beanFactory;

    /**
     * Lazy initialized holders per channel.
     */
    private final Map<String, ChannelHolder> holders = new ConcurrentHashMap<>();

    public NotificationConnector get(String channel) {
        Objects.requireNonNull(channel, "channel");
        ChannelHolder holder = holders.computeIfAbsent(channel, this::initHolderLazy);
        return holder.pick();
    }

    public ConnectorProperties.ChannelConfig getConfig(String channel) {
        Objects.requireNonNull(channel, "channel");
        ChannelHolder holder = holders.get(channel);
        if (holder == null) {
            return null;
        }
        return holder.activeCfg.get();
    }

    /**
     * Called by reloader when config changes.
     */
    void hotReloadAll(Map<String, ConnectorProperties.ChannelConfig> newConfigMap) {
        // Update existing holders, do not eagerly create new channels.
        newConfigMap.forEach((channel, cfg) -> {
            ChannelHolder existing = holders.get(channel);
            if (existing != null) {
                existing.reloadIfChanged(cfg, beanFactory, channel);
            }
        });

        // Optionally handle removed channels: keep existing as-is or mark disabled.
        // Here: we keep existing, but log if removed.
        holders.keySet().forEach(channel -> {
            if (!newConfigMap.containsKey(channel)) {
                log.warn("Channel {} removed from config; existing holder retained", channel);
            }
        });
    }

    private ChannelHolder initHolderLazy(String channel) {
        ConnectorProperties.ChannelConfig cfg = connectorProperties.getChannel().get(channel);
        if (cfg == null) {
            throw new IllegalArgumentException("No connector configured for channel: " + channel);
        }
        ChannelHolder holder = new ChannelHolder();
        holder.reloadIfChanged(cfg, beanFactory, channel);
        log.info("Lazy-initialized connector channel {}", channel);
        return holder;
    }

    /**
     * Holds active connector instances for a channel and swaps them atomically on reload.
     */
    static final class ChannelHolder {
        private final AtomicReference<ConnectorProperties.ChannelConfig> activeCfg = new AtomicReference<>();
        private final AtomicReference<List<NotificationConnector>> activeInstances = new AtomicReference<>(List.of());
        private final AtomicInteger rr = new AtomicInteger(0);

        NotificationConnector pick() {
            List<NotificationConnector> list = activeInstances.get();
            if (list.isEmpty()) {
                throw new IllegalStateException("Connector holder not initialized");
            }
            int i = Math.floorMod(rr.getAndIncrement(), list.size());
            return list.get(i);
        }

        public ConnectorProperties.ChannelConfig getActiveConfig() {
            return activeCfg.get();
        }

        void reloadIfChanged(ConnectorProperties.ChannelConfig newCfg,
                             AutowireCapableBeanFactory beanFactory,
                             String channel) {

            ConnectorProperties.ChannelConfig cur = activeCfg.get();

            if (cur != null
                    && Objects.equals(cur.getClazz(), newCfg.getClazz())
                    && cur.getInstances() == newCfg.getInstances()) {
                return; // no-op
            }

            if (newCfg.getClazz() == null || newCfg.getClazz().isBlank()) {
                throw new IllegalStateException("connector.channel." + channel + ".class is missing");
            }
            if (newCfg.getInstances() <= 0) {
                throw new IllegalStateException("connector.channel." + channel + ".instances must be > 0");
            }

            List<NotificationConnector> built = ConnectorBuilder.buildInstances(
                    beanFactory, newCfg.getClazz(), newCfg.getInstances()
            );

            activeInstances.set(built);
            activeCfg.set(cloneCfg(newCfg));
            rr.set(0);
        }

        private ConnectorProperties.ChannelConfig cloneCfg(ConnectorProperties.ChannelConfig cfg) {
            ConnectorProperties.ChannelConfig c = new ConnectorProperties.ChannelConfig();
            c.setClazz(cfg.getClazz());
            c.setInstances(cfg.getInstances());
            return c;
        }
    }

    static final class ConnectorBuilder {
        static List<NotificationConnector> buildInstances(
                AutowireCapableBeanFactory beanFactory,
                String className,
                int instances) {

            try {
                Class<?> clazz = Class.forName(className);
                if (!NotificationConnector.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException(className + " does not implement NotificationConnector");
                }

                @SuppressWarnings("unchecked")
                Class<? extends NotificationConnector> typed =
                        (Class<? extends NotificationConnector>) clazz;

                return (List<NotificationConnector>) java.util.stream.IntStream.range(0, instances)
                        .mapToObj(i -> beanFactory.createBean(typed))
                        .toList();

            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate connector class: " + className, e);
            }
        }
    }


}
