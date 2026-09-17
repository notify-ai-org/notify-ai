-- Apply after 003_unified_channel_config.sql, before starting the updated app.
-- Keep existing credential version IDs; no AWS resources or secret values change.
BEGIN;

ALTER TABLE secret_metadata RENAME COLUMN aws_version_id TO version_id;

-- PostgreSQL also drops the old ACTIVE-state check that references this column.
ALTER TABLE secret_metadata DROP COLUMN aws_secret_arn;

ALTER TABLE secret_metadata
    ADD CONSTRAINT secret_metadata_active_version_check
    CHECK (status <> 'ACTIVE' OR (version_id IS NOT NULL AND pending_operation IS NULL));

COMMIT;
