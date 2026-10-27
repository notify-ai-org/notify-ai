-- Standby is replaced by the DEFERRED processor result. Apply BEFORE deploying the release that
-- removes standby: the status check below otherwise rejects every deferred capture, and Hibernate's
-- ddl-auto does not change check constraints.
--
-- Existing STANDBY captures become DEFERRED, so later events for the same subject can still include
-- them. The standby snapshot table and the capture's standby expiry are no longer used.
-- Safe to re-run.
BEGIN;

ALTER TABLE event_capture DROP CONSTRAINT IF EXISTS event_capture_status_check;
UPDATE event_capture SET status = 'DEFERRED' WHERE status = 'STANDBY';
ALTER TABLE event_capture ADD CONSTRAINT event_capture_status_check CHECK
  (status IN ('PROCESSING','PROCESSED','DISPATCHED','SUPPRESSED','DEFERRED','REPRESSED','FAILED'));

ALTER TABLE event_capture DROP COLUMN IF EXISTS standby_until;
DROP TABLE IF EXISTS standby_event;

-- Deferred events are looked up per subject by status and time.
CREATE INDEX IF NOT EXISTS idx_capture_subject_deferred
  ON event_capture (tenant_id, subject, timestamp DESC) WHERE status = 'DEFERRED';

COMMIT;
