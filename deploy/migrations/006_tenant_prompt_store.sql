-- Apply before deploying the tenant prompt store. Content lives in the artifact engine/S3.
BEGIN;

CREATE TABLE tenant_prompt_versions (
    tenant_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    version_id VARCHAR(36) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, version_id),
    UNIQUE (tenant_id, artifact_id)
);
CREATE INDEX tenant_prompt_versions_recent
    ON tenant_prompt_versions (tenant_id, agent_id, created_at DESC, version_id DESC);

CREATE TABLE tenant_active_prompts (
    tenant_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    version_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (tenant_id, agent_id),
    FOREIGN KEY (tenant_id, agent_id, version_id)
        REFERENCES tenant_prompt_versions (tenant_id, agent_id, version_id)
);

COMMIT;
