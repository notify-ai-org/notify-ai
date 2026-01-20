package com.example.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation to enable the Notification Engine SDK.
 * Place on a @Configuration class. The base package is scanned for @Event, @Rule,
 * @Callback, @Vocabulary, @Model, @VocabularySupplier, and @SubjectSupplier.
 *
 * Set notify.base-package in application.yml or basePackage here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableNotify {
    /**
     * Base package to scan for notification-related annotations.
     */
    String basePackage() default "";
}
