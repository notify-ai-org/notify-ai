package com.notify.agent.service;

import com.notify.agent.ConfigEntryRepository;
import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.models.ConfigEntry;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
// import org.springframework.kafka.annotation.KafkaListener; // Kafka disabled for now
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified dynamic-configuration service.
 * <p>
 * Responsibilities:
 * <ol>
 * <li>Auto-discovers all Spring beans whose fields are annotated with
 * {@link ManagedConfiguration} (via {@link BeanPostProcessor}).</li>
 * <li>On startup ({@code @PostConstruct}) loads all {@link ConfigEntry} rows
 * from the DB and applies them to the discovered fields using reflection.</li>
 * <li>Exposes {@link #refreshAll()} and {@link #refresh(String)} for DB-sourced
 * config updates (triggered via Kafka events).</li>
 * <li>Exposes {@link #refreshConfigMapKeys()} for ConfigMap-sourced config,
 * triggered by Spring Actuator refresh or a REST endpoint after K8s
 * ConfigMap changes.</li>
 * </ol>
 */
@Service
public class ManagedConfigService implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ManagedConfigService.class);

    private final ConfigEntryRepository configEntryRepository;
    private final Environment environment;

    /**
     * Mapping from config key → list of field targets (bean + field) that should
     * receive the value for that key.
     */
    private final Map<String, List<FieldTarget>> registry = new ConcurrentHashMap<>();

    public ManagedConfigService(ConfigEntryRepository configEntryRepository, Environment environment) {
        this.configEntryRepository = configEntryRepository;
        this.environment = environment;
    }

    // -----------------------------------------------------------------------
    // BeanPostProcessor – auto-discover annotated fields
    // -----------------------------------------------------------------------

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        // Walk the class hierarchy to catch inherited fields
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                ManagedConfiguration ann = field.getAnnotation(ManagedConfiguration.class);
                if (ann != null) {
                    field.setAccessible(true);
                    registry.computeIfAbsent(ann.key(), k -> new ArrayList<>())
                            .add(new FieldTarget(bean, field, ann.source()));
                    log.debug("Registered managed config: key={}, bean={}, field={}",
                            ann.key(), beanName, field.getName());
                }
            }
            clazz = clazz.getSuperclass();
        }
        return bean;
    }

    // -----------------------------------------------------------------------
    // Startup – seed DB-sourced defaults, then apply all values
    // -----------------------------------------------------------------------

    @PostConstruct
    public void refreshAll() throws Exception {
        // 1. Seed missing DB-sourced keys with their current default values
        seedDefaults();

        // 2. Load all entries and apply
        List<ConfigEntry> entries = configEntryRepository.findAll();
        log.info("ManagedConfigService: applying {} config entries", entries.size());

        for (ConfigEntry entry : entries) {
            applyValue(entry.getConfigKey(), entry.getConfigValue());
        }
    }

    /**
     * For every DB-sourced {@code @ManagedConfiguration} field, checks whether
     * the key already exists in the {@code config_entries} table. If not,
     * reads the field's current (default) value and persists it so that an
     * operator can later modify it through the config service.
     */
    private void seedDefaults() throws Exception {
        for (Map.Entry<String, List<FieldTarget>> entry : registry.entrySet()) {
            String key = entry.getKey();

            // Only seed DB-sourced keys
            FieldTarget representative = entry.getValue().stream()
                    .filter(ft -> ft.source == ManagedConfiguration.ConfigSource.DB)
                    .findFirst()
                    .orElse(null);
            if (representative == null) {
                continue;
            }

            if (configEntryRepository.existsById(key)) {
                continue; // already in DB
            }

            try {
                Object defaultValue = representative.field.get(representative.bean);
                String serialized = defaultValue == null ? "" : String.valueOf(defaultValue);

                ConfigEntry configEntry = new ConfigEntry();
                configEntry.setConfigKey(key);
                configEntry.setConfigValue(serialized);
                configEntry.setDescription("Auto-seeded default for " + key);
                configEntryRepository.save(configEntry);

                log.info("Seeded default config: {}={}", key, serialized);
            } catch (Exception e) {
                log.warn("Failed to seed default for key '{}': {}", key, e.getMessage());
                throw e;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Single-key refresh (DB-sourced)
    // -----------------------------------------------------------------------

    /**
     * Refresh a single config key from the database and apply it to all
     * registered field targets.
     */
    public void refresh(String key) throws Exception {
        Optional<ConfigEntry> entry = configEntryRepository.findById(key);
        if (entry.isPresent()) {
            applyValue(key, entry.get().getConfigValue());
        } else {
            log.warn("Config key '{}' not found in DB", key);
        }
    }

    // -----------------------------------------------------------------------
    // ConfigMap / @RefreshScope – Environment-based refresh
    // -----------------------------------------------------------------------

    /**
     * Re-reads all {@code CONFIG_MAP}-sourced {@code @ManagedConfiguration}
     * fields from the Spring {@link Environment} and applies updated values.
     * <p>
     * Call this after a K8s ConfigMap change triggers Spring Actuator
     * {@code /actuator/refresh}, or from a REST endpoint / event listener.
     */
    public void refreshConfigMapKeys() throws Exception {
        log.info("Refreshing all CONFIG_MAP-sourced managed configuration keys");

        for (Map.Entry<String, List<FieldTarget>> entry : registry.entrySet()) {
            String key = entry.getKey();

            for (FieldTarget target : entry.getValue()) {
                if (target.source != ManagedConfiguration.ConfigSource.CONFIG_MAP) {
                    continue;
                }

                String newValue = environment.getProperty(key);
                if (newValue != null) {
                    try {
                        Object coerced = coerce(newValue, target.field.getType());
                        target.field.set(target.bean, coerced);
                        log.info("ConfigMap refresh: {}={} → {}.{}",
                                key, newValue,
                                target.bean.getClass().getSimpleName(),
                                target.field.getName());
                    } catch (Exception e) {
                        log.error("Failed to refresh ConfigMap key {}={} on {}.{}: {}",
                                key, newValue,
                                target.bean.getClass().getSimpleName(),
                                target.field.getName(),
                                e.getMessage());
                        throw e;
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Kafka listener (disabled by default until Kafka is enabled)
    // -----------------------------------------------------------------------

    // Uncomment when Kafka is enabled:
    // @KafkaListener(topics = "${config.kafka-topic:config-updates}", groupId =
    // "config-refresh-group")
    public void onConfigChangeEvent(String message) throws Exception {
        try {
            // Expected payload: a JSON string with a "key" field, or "ALL" for full refresh
            // Simple parsing: if message is "ALL", refresh everything; otherwise treat as
            // key
            String trimmed = message.trim();
            if ("ALL".equalsIgnoreCase(trimmed)) {
                refreshAll();
            } else {
                // Try to extract key from JSON or treat as plain key
                String key = extractKeyFromMessage(trimmed);
                refresh(key);
            }
        } catch (Exception e) {
            log.error("Failed to process config change event: {}", message, e);
            throw e;
        }
    }

    public void updateConfigMap(Map<String, Object> configMap) throws Exception {
        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            List<FieldTarget> targets = registry.get(key);
            if (targets != null) {
                try {
                    applyValue(key, value.toString());
                } catch (Exception e) {
                    log.error("Failed to refresh ConfigMap key {}={} : {}",
                            key, value,
                            e.getMessage());
                    throw e;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void applyValue(String key, String rawValue) throws Exception {
        List<FieldTarget> targets = registry.get(key);
        if (targets == null || targets.isEmpty()) {
            log.trace("No registered targets for config key '{}'", key);
            return;
        }

        for (FieldTarget target : targets) {
            try {
                Object coerced = coerce(rawValue, target.field.getType());
                target.field.set(target.bean, coerced);
                log.info("Applied config: {}={} → {}.{}",
                        key, rawValue,
                        target.bean.getClass().getSimpleName(),
                        target.field.getName());
            } catch (Exception e) {
                log.error("Failed to apply config {}={} to {}.{}: {}",
                        key, rawValue,
                        target.bean.getClass().getSimpleName(),
                        target.field.getName(),
                        e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Coerce a string value to the target field type.
     */
    private Object coerce(String value, Class<?> type) {
        if (type == String.class)
            return value;
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        if (type == Duration.class)
            return parseDuration(value);
        throw new IllegalArgumentException("Unsupported config type: " + type.getName());
    }

    /**
     * Parse duration strings like "30s", "5m", "1h", "100ms" or ISO-8601 durations.
     */
    private Duration parseDuration(String value) {
        value = value.trim();

        // Handle common suffixes: ms, s, m, h, d
        if (value.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(value.replace("ms", "").trim()));
        }
        if (value.endsWith("s") && !value.startsWith("P")) {
            return Duration.ofSeconds(Long.parseLong(value.replace("s", "").trim()));
        }
        if (value.endsWith("m") && !value.startsWith("P")) {
            return Duration.ofMinutes(Long.parseLong(value.replace("m", "").trim()));
        }
        if (value.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(value.replace("h", "").trim()));
        }
        if (value.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(value.replace("d", "").trim()));
        }

        // Fall back to ISO-8601 (e.g. "PT30S", "PT5M")
        return Duration.parse(value);
    }

    /**
     * Extract a config key from a Kafka message.
     * Supports plain strings or simple JSON like {"key": "some.key"}.
     */
    private String extractKeyFromMessage(String message) {
        // Simple JSON extraction
        if (message.startsWith("{") && message.contains("\"key\"")) {
            int idx = message.indexOf("\"key\"");
            int colon = message.indexOf(":", idx);
            int start = message.indexOf("\"", colon + 1);
            int end = message.indexOf("\"", start + 1);
            if (start >= 0 && end > start) {
                return message.substring(start + 1, end);
            }
        }
        // Treat as plain key
        return message;
    }

    // -----------------------------------------------------------------------
    // Internal record
    // -----------------------------------------------------------------------

    private static class FieldTarget {
        final Object bean;
        final Field field;
        final ManagedConfiguration.ConfigSource source;

        FieldTarget(Object bean, Field field, ManagedConfiguration.ConfigSource source) {
            this.bean = bean;
            this.field = field;
            this.source = source;
        }
    }
}
