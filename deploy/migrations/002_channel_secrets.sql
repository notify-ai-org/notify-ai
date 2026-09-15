-- Apply once with the same database/schema used by the notification service.
-- No credential values are stored in these tables.
CREATE TABLE tenant_channel_config (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    type varchar(32) NOT NULL,
    provider varchar(32) NOT NULL,
    credential_type varchar(64) NOT NULL,
    secret_ref varchar(64),
    enabled boolean NOT NULL DEFAULT false,
    settings_json text NOT NULL DEFAULT '{}',
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    CHECK (type IN ('EMAIL','SMS','WEBHOOK','IN_APP','WHATSAPP','PUSH'))
);
CREATE INDEX idx_channel_tenant_type ON tenant_channel_config(tenant_id, type, enabled);
CREATE TABLE secret_metadata (
    id uuid PRIMARY KEY,
    secret_ref varchar(64) NOT NULL UNIQUE,
    tenant_id uuid NOT NULL,
    channel_id uuid NOT NULL,
    provider varchar(32) NOT NULL,
    credential_type varchar(64) NOT NULL,
    aws_secret_arn varchar(2048),
    status varchar(32) NOT NULL,
    aws_version_id varchar(128),
    pending_operation varchar(16),
    operation_token varchar(64),
    operation_principal_id varchar(255),
    operation_workload_id varchar(255),
    operation_trace_id varchar(128),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, channel_id),
    FOREIGN KEY (tenant_id,channel_id) REFERENCES tenant_channel_config(tenant_id,id),
    CHECK (status IN ('ACTIVE','ROTATING','REVOKED','DELETION_PENDING')),
    CHECK (pending_operation IS NULL OR pending_operation IN ('CREATE','UPDATE','ROTATE','DELETE')),
    CHECK (status <> 'ACTIVE' OR (aws_secret_arn IS NOT NULL AND aws_version_id IS NOT NULL AND pending_operation IS NULL))
);
CREATE INDEX idx_secret_binding_status ON secret_metadata(tenant_id,channel_id,secret_ref,status);
CREATE INDEX idx_secret_reconciliation ON secret_metadata(updated_at) WHERE pending_operation IS NOT NULL;
CREATE TABLE secret_access_audit (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    channel_id uuid,
    secret_ref varchar(64),
    principal_id varchar(255) NOT NULL,
    workload_id varchar(255),
    operation varchar(64) NOT NULL,
    result varchar(32) NOT NULL,
    trace_id varchar(128),
    timestamp timestamptz NOT NULL,
    failure_reason_code varchar(64)
);
CREATE INDEX idx_secret_audit_tenant_time ON secret_access_audit(tenant_id,timestamp);
ALTER TABLE notification_job ADD COLUMN IF NOT EXISTS channel_id uuid;
-- Existing rows require an explicit tenant-owned channel assignment before replay.
