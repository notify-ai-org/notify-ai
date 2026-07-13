-- Notify.ai PostgreSQL migration bundle.
-- Safe to re-run on existing EC2 databases.

-- Stores SQL-backed episodic-memory pages used by the event processor.
CREATE TABLE IF NOT EXISTS memory_page (
    page_id VARCHAR(255) NOT NULL,
    namespace VARCHAR(255),
    correlation_id VARCHAR(255),
    summary TEXT,
    severity_max VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    embedding BYTEA,
    PRIMARY KEY (page_id)
);

CREATE INDEX IF NOT EXISTS idx_memory_page_namespace
    ON memory_page (namespace);

CREATE INDEX IF NOT EXISTS idx_memory_page_correlation_id
    ON memory_page (correlation_id);

-- Existing databases may have created message_templates.template as
-- VARCHAR(2048), but generated EMAIL templates are full HTML documents.
DO $$
BEGIN
    IF to_regclass('public.message_templates') IS NOT NULL THEN
        ALTER TABLE message_templates
            ALTER COLUMN template TYPE TEXT;
    END IF;
END $$;

-- Domain content stores one row per client + type + keyName. Some older
-- databases accidentally used a unique client/type constraint or index.
DO $$
BEGIN
    IF to_regclass('public.domain_content') IS NOT NULL THEN
        ALTER TABLE domain_content
            DROP CONSTRAINT IF EXISTS idx_domain_content_client_type;

        DROP INDEX IF EXISTS idx_domain_content_client_type;

        CREATE INDEX IF NOT EXISTS idx_domain_content_client_type
            ON domain_content (clientId, type);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_domain_content_client_type_key
            ON domain_content (clientId, type, keyName);
    END IF;
END $$;

-- Persists NotificationJob.subjects, and allows large templates/results.
DO $$
BEGIN
    IF to_regclass('public.notificationjob') IS NOT NULL THEN
        ALTER TABLE notificationjob ADD COLUMN IF NOT EXISTS subjects_json TEXT;
        ALTER TABLE notificationjob ALTER COLUMN template TYPE TEXT;
    END IF;

    IF to_regclass('public.notificationattemptlog') IS NOT NULL THEN
        ALTER TABLE notificationattemptlog ALTER COLUMN template TYPE TEXT;
        ALTER TABLE notificationattemptlog ALTER COLUMN error TYPE TEXT;
        ALTER TABLE notificationattemptlog ALTER COLUMN result TYPE TEXT;
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS notificationjobid VARCHAR(255);
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS eventname VARCHAR(255);
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS status VARCHAR(255);
    END IF;

    IF to_regclass('public.notification_attempt_log') IS NOT NULL THEN
        ALTER TABLE notification_attempt_log ALTER COLUMN template TYPE TEXT;
        ALTER TABLE notification_attempt_log ALTER COLUMN error TYPE TEXT;
        ALTER TABLE notification_attempt_log ALTER COLUMN result TYPE TEXT;
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS notification_job_id VARCHAR(255);
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS event_name VARCHAR(255);
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS status VARCHAR(255);
    END IF;
END $$;

-- Adds the channel selected for a generated event schedule. Existing
-- deployments may have compact or snake_case table names.
DO $$
BEGIN
    IF to_regclass('public.eventschedule') IS NOT NULL THEN
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS channel VARCHAR(255);
    END IF;

    IF to_regclass('public.event_schedule') IS NOT NULL THEN
        ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS channel VARCHAR(255);
    END IF;
END $$;
