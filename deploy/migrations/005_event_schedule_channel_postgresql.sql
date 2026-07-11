-- Adds the channel selected for a generated event schedule.
-- Existing deployments may have either Hibernate's default table name
-- (eventschedule) or a snake_case table name (event_schedule).
DO $$
BEGIN
    IF to_regclass('public.eventschedule') IS NOT NULL THEN
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS channel VARCHAR(255);
    END IF;

    IF to_regclass('public.event_schedule') IS NOT NULL THEN
        ALTER TABLE event_schedule ADD COLUMN IF NOT EXISTS channel VARCHAR(255);
    END IF;
END $$;
