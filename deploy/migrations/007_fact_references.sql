-- Apply to an existing facts table before deploying the fact reference schema.
-- Unknown legacy categories require explicit classification; do not guess their scope.
BEGIN;

ALTER TABLE facts ADD COLUMN IF NOT EXISTS fact_event VARCHAR(512);
ALTER TABLE facts ADD COLUMN IF NOT EXISTS entity_ref TEXT;
ALTER TABLE facts ADD COLUMN IF NOT EXISTS fact_subject VARCHAR(256);

UPDATE facts SET fact_subject = fact_user
WHERE fact_subject IS NULL AND fact_user IS NOT NULL;

-- Keep the original classification in evidence before normalizing known legacy types.
-- Invalid historical evidence is left intact rather than preventing reference backfill.
CREATE OR REPLACE FUNCTION pg_temp.fact_evidence(value TEXT) RETURNS JSONB
LANGUAGE plpgsql AS $$
BEGIN
    RETURN value::jsonb;
EXCEPTION WHEN invalid_text_representation THEN
    RETURN NULL;
END;
$$;

UPDATE facts
SET evidence_json = (pg_temp.fact_evidence(evidence_json)
                    || jsonb_build_object('legacyFactType', fact_type))::text
WHERE upper(trim(fact_type)) IN ('EVENT_EMITTED', 'EVENT_SUPPRESSED', 'EVENT_FAILED',
                                'NOTIFICATION_DELIVERED', 'NOTIFICATION_ACKNOWLEDGED')
  AND jsonb_typeof(pg_temp.fact_evidence(evidence_json)) = 'object';

UPDATE facts SET fact_event = COALESCE(
    pg_temp.fact_evidence(evidence_json)->>'eventName',
    pg_temp.fact_evidence(evidence_json)->>'eventKey',
    CASE WHEN jsonb_typeof(pg_temp.fact_evidence(evidence_json)->'event') = 'string'
         THEN pg_temp.fact_evidence(evidence_json)->>'event' END)
WHERE fact_event IS NULL;

UPDATE facts SET fact_type = CASE upper(trim(fact_type))
    WHEN 'EVENT_EMITTED' THEN 'event'
    WHEN 'EVENT_SUPPRESSED' THEN 'event'
    WHEN 'EVENT_FAILED' THEN 'event'
    WHEN 'NOTIFICATION_DELIVERED' THEN 'notification'
    WHEN 'NOTIFICATION_ACKNOWLEDGED' THEN 'notification'
    ELSE lower(trim(fact_type)) END;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM facts WHERE fact_type IS NULL OR
               fact_type NOT IN ('event', 'notification', 'subject', 'entity')) THEN
        RAISE EXCEPTION 'Classify legacy facts with missing/unknown fact_type before applying 007';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'facts'::regclass AND conname = 'facts_category_check') THEN
        ALTER TABLE facts ADD CONSTRAINT facts_category_check
            CHECK (fact_type IN ('event', 'notification', 'subject', 'entity'));
    END IF;
END;
$$;

ALTER TABLE facts ALTER COLUMN fact_type SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_facts_tenant_subject ON facts (tenant_id, fact_subject);
CREATE INDEX IF NOT EXISTS idx_facts_tenant_event ON facts (tenant_id, fact_event);

COMMIT;
