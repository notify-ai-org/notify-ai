-- Apply before deploying adaptive event processing and schedule mutations.
BEGIN;
ALTER TABLE event_capture ADD COLUMN IF NOT EXISTS subject varchar(255);
ALTER TABLE event_capture ADD COLUMN IF NOT EXISTS payload_json text;
ALTER TABLE event_capture ADD COLUMN IF NOT EXISTS facts_json text;
ALTER TABLE event_capture ADD COLUMN IF NOT EXISTS standby_until timestamptz;
CREATE INDEX IF NOT EXISTS idx_capture_subject_history ON event_capture (tenant_id, subject, timestamp DESC);
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS event_ref varchar(255);
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS subject varchar(255);
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS mutation_pending boolean NOT NULL DEFAULT false;
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS revoked boolean NOT NULL DEFAULT false;
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 0;
ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE notification_job ADD COLUMN IF NOT EXISTS schedule_revision bigint;
CREATE INDEX IF NOT EXISTS idx_schedule_subject ON event_schedule (tenant_id, subject, revoked);
CREATE INDEX IF NOT EXISTS idx_notification_schedule ON notification_job (schedule_id);
-- Legacy shared schedules deliberately retain null event/subject and revision 0.
-- They remain operational but are not exposed for subject-specific agent mutations.
-- Hibernate-generated enum checks, when present, need the new capture outcomes.
ALTER TABLE event_capture DROP CONSTRAINT IF EXISTS event_capture_status_check;
ALTER TABLE event_capture ADD CONSTRAINT event_capture_status_check CHECK
  (status IN ('PROCESSING','PROCESSED','DISPATCHED','SUPPRESSED','STANDBY','REPRESSED','FAILED'));
COMMIT;
