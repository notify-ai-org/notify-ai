-- Apply after 004_secret_metadata_version_id.sql, before starting the updated app.
-- The rule includes disabled channels and channels with revoked credentials.
-- Existing duplicates must be reviewed first. This migration never deletes channels,
-- their secrets, settings, audits, or delivery history to choose a winner.
-- Find duplicates with:
-- SELECT tenant_id, type, count(*) FROM tenant_channel_config
-- GROUP BY tenant_id, type HAVING count(*) > 1;
BEGIN;

ALTER TABLE tenant_channel_config
    ADD CONSTRAINT uk_tenant_channel_type UNIQUE (tenant_id, type);

COMMIT;
