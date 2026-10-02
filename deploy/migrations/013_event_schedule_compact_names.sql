-- Adaptive event scheduling columns for databases whose schedule table uses
-- compact names (eventschedule / mutationpending) instead of snake_case.
--
-- 008_adaptive_event_scheduling.sql targets event_schedule.mutation_pending
-- and friends, so on compact-named databases it never applies. Hibernate's
-- ddl-auto cannot add these NOT NULL columns to a table that already has rows,
-- and the app then fails at runtime when it reads or updates schedules.
--
-- Defaults match 008: existing schedules are not revoked, have no pending
-- mutation, and start at revision/version 0. Safe to re-run; a no-op where the
-- columns already exist or the table is absent.
DO $$
BEGIN
    IF to_regclass('public.eventschedule') IS NOT NULL THEN
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS mutationpending BOOLEAN NOT NULL DEFAULT false;
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT false;
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE eventschedule ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

        -- Same lookup index as 008, when the subject columns are present.
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'eventschedule' AND column_name = 'tenant_id'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'eventschedule' AND column_name = 'subject'
        ) THEN
            CREATE INDEX IF NOT EXISTS idx_eventschedule_subject ON eventschedule (tenant_id, subject, revoked);
        END IF;
    END IF;
END $$;
