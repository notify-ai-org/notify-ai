package com.example.agent.models.metadata;

import java.lang.reflect.Method;

public class EventMetadata {
    private final String key;
    private final String description;
    private final String version;
    private final Method method;
    private final Class<?> declaringClass;

    public EventMetadata(String key, String description, String version, Method method, Class<?> declaringClass) {
        this.key = key;
        this.description = description;
        this.version = version != null ? version : "v1";
        this.method = method;
        this.declaringClass = declaringClass;
    }

    public String getKey() { return key; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public Method getMethod() { return method; }
    public Class<?> getDeclaringClass() { return declaringClass; }
}
