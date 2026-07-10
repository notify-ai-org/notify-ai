-- Persists NotificationJob.subjects, which is a transient Java field
-- serialized into this TEXT column before insert/update.
ALTER TABLE notification_job
    ADD COLUMN IF NOT EXISTS subjects_json TEXT;
