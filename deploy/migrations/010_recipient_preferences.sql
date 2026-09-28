-- Per-recipient delivery preferences. The engine suppresses opted-out sends and
-- non-critical sends inside quiet hours; planning agents read them via a tool.
BEGIN;

CREATE TABLE IF NOT EXISTS recipient_preference (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    opted_out BOOLEAN NOT NULL DEFAULT FALSE,
    opted_out_channels VARCHAR(255) NOT NULL DEFAULT '',
    preferred_channel VARCHAR(32),
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    time_zone VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    validated BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMPTZ,
    validated_by VARCHAR(255),
    correlation_id VARCHAR(255),
    UNIQUE (tenant_id, subject_id)
);

-- Jobs record the stable recipient identity so the engine can look up preferences;
-- subjects only carry delivery addresses. Jobs created before this have no recipient.
ALTER TABLE IF EXISTS notification_job ADD COLUMN IF NOT EXISTS recipient_id VARCHAR(255);

COMMIT;
