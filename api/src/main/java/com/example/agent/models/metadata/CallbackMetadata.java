package com.example.agent.models.metadata;

import java.lang.reflect.Method;
import com.example.agent.annotations.Callback.When;

public class CallbackMetadata {
    private final String event;
    private final When when;
    private final Method method;
    private final Class<?> declaringClass;

    public CallbackMetadata(String event, When when, Method method, Class<?> declaringClass) {
        this.event = event;
        this.when = when;
        this.method = method;
        this.declaringClass = declaringClass;
    }

    public String getEvent() { return event; }
    public When getWhen() { return when; }
    public Method getMethod() { return method; }
    public Class<?> getDeclaringClass() { return declaringClass; }
}
