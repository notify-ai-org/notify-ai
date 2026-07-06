-- Required for existing PostgreSQL databases created with template as VARCHAR(2048).
-- EMAIL templates are full HTML documents and can exceed 2048 characters.
ALTER TABLE message_templates
    ALTER COLUMN template TYPE TEXT;
