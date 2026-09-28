-- DLQ replays run on a dedicated engine thread: records move PENDING -> REPLAYING -> REPLAYED
-- (or back to PENDING with replay_error when the dispatch fails).
BEGIN;

ALTER TABLE IF EXISTS dead_letter_record ADD COLUMN IF NOT EXISTS replay_requested_at TIMESTAMPTZ;
ALTER TABLE IF EXISTS dead_letter_record ADD COLUMN IF NOT EXISTS replay_error VARCHAR(1024);
-- Hibernate-created enum check constraints predate REPLAYING and are not widened by ddl-auto.
ALTER TABLE IF EXISTS dead_letter_record DROP CONSTRAINT IF EXISTS dead_letter_record_replay_status_check;

COMMIT;
