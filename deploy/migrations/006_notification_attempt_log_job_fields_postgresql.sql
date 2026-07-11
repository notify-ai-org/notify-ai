-- Adds job/event/status fields rendered by the notification logs portal.
-- Existing deployments may have either Hibernate's default compact table name
-- (notificationattemptlog) or a snake_case table name (notification_attempt_log).
DO $$
BEGIN
    IF to_regclass('public.notificationattemptlog') IS NOT NULL THEN
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS notificationjobid VARCHAR(255);
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS eventname VARCHAR(255);
        ALTER TABLE notificationattemptlog ADD COLUMN IF NOT EXISTS status VARCHAR(255);
    END IF;

    IF to_regclass('public.notification_attempt_log') IS NOT NULL THEN
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS notification_job_id VARCHAR(255);
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS event_name VARCHAR(255);
        ALTER TABLE notification_attempt_log ADD COLUMN IF NOT EXISTS status VARCHAR(255);
    END IF;
END $$;
