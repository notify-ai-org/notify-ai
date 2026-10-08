-- Standby events move from Redis (notify:standby:*) to Postgres, so holding an event no longer
-- requires Redis. Snapshots still expire individually; expired rows are deleted on write.
BEGIN;

CREATE TABLE IF NOT EXISTS standby_event (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    capture_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    validated BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMPTZ,
    validated_by VARCHAR(255),
    correlation_id VARCHAR(255),
    UNIQUE (tenant_id, subject, capture_id)
);
CREATE INDEX IF NOT EXISTS idx_standby_event_scope ON standby_event (tenant_id, subject, expires_at);

COMMIT;
