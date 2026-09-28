-- Multi-language templates driven by recipient preferences. Generated templates are
-- stored per event, channel and language; existing rows remain the default (NULL) copy.
BEGIN;

ALTER TABLE recipient_preference ADD COLUMN IF NOT EXISTS language VARCHAR(35);
ALTER TABLE IF EXISTS message_templates ADD COLUMN IF NOT EXISTS language VARCHAR(35);
CREATE INDEX IF NOT EXISTS message_templates_event_channel_language
    ON message_templates (tenant_id, lower(event_name), lower(channel), language);

COMMIT;
