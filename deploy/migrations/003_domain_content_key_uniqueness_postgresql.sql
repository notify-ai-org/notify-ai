-- Existing databases may have accidentally created this as a unique
-- client/type constraint, which prevents multiple domain content keys
-- such as BUSINESS_NAME and BUSINESS_LOGO_URL for the same client.
ALTER TABLE domain_content
    DROP CONSTRAINT IF EXISTS idx_domain_content_client_type;

DROP INDEX IF EXISTS idx_domain_content_client_type;

CREATE INDEX IF NOT EXISTS idx_domain_content_client_type
    ON domain_content (clientId, type);

CREATE UNIQUE INDEX IF NOT EXISTS idx_domain_content_client_type_key
    ON domain_content (clientId, type, keyName);
