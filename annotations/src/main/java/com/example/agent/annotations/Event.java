package com.example.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Event {
    String key();                       // e.g. "order.placed"
    String version() default "v1";
    Class<?> payload() default Void.class; // optional override
}

