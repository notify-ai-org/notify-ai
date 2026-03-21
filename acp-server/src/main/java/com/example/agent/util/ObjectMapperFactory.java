package com.example.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.fasterxml.jackson.core.StreamReadConstraints;

/**
 * Factory for creating ObjectMapper instances with Java 8 date/time support.
 * All code in this module should use this instead of
 * {@code new ObjectMapper()}.
 */
public final class ObjectMapperFactory {

    private ObjectMapperFactory() {
    }

    /**
     * Create a new ObjectMapper pre-configured with the JavaTimeModule
     * so that java.time types (Instant, LocalDate, etc.) are handled correctly.
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder().maxNumberLength(10000).build()
        );
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
