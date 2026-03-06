package com.example.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as dynamically configurable by the
 * {@code ManagedConfigService}.
 * The service discovers annotated fields via reflection and updates them when
 * config changes arrive (from DB or ConfigMap via Kafka / @RefreshScope).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ManagedConfiguration {

    /** The configuration key, e.g. {@code "agent.orchestrator.core-pool-size"}. */
    String key();

    /** Where the canonical value is stored. */
    ConfigSource source() default ConfigSource.DB;

    enum ConfigSource {
        /** Value is stored in the {@code config_entries} database table. */
        DB,
        /** Value is managed via Kubernetes ConfigMap / Spring @RefreshScope. */
        CONFIG_MAP
    }
}
