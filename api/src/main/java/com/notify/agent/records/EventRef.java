package com.notify.agent.records;

import java.time.Instant;

public record EventRef(String eventId, String eventType, String severity, Instant timestamp) {
}
