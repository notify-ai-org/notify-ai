package com.notify.agent.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notify.agent.models.EventCapture;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class EventCaptureSerializer implements Serializer<EventCapture> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventCaptureSerializer() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No configuration needed
    }

    @Override
    public byte[] serialize(String topic, EventCapture data) {
        try {
            if (data == null) {
                return null;
            }
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException("Error when serializing EventCapture to byte[]", e);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
