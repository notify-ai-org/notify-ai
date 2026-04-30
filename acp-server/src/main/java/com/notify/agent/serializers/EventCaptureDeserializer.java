package com.notify.agent.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notify.agent.models.EventCapture;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class EventCaptureDeserializer implements Deserializer<EventCapture> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventCaptureDeserializer() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No configuration needed
    }

    @Override
    public EventCapture deserialize(String topic, byte[] data) {
        try {
            if (data == null) {
                return null;
            }
            return objectMapper.readValue(data, EventCapture.class);
        } catch (Exception e) {
            throw new SerializationException("Error when deserializing byte[] to EventCapture", e);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
