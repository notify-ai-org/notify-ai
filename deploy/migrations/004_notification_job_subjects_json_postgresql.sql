-- Persists NotificationJob.subjects, which is a transient Java field
-- serialized into this TEXT column before insert/update.
ALTER TABLE notificationjob ADD COLUMN IF NOT EXISTS subjects_json TEXT;
ALTER TABLE notificationjob ALTER COLUMN template TYPE text;
ALTER TABLE notificationattemptlog ALTER COLUMN template TYPE text;
ALTER TABLE notificationattemptlog ALTER COLUMN error TYPE text;
ALTER TABLE notificationattemptlog ALTER COLUMN result TYPE text;