-- Preserve entity identities and retrieval filters on SQL memory pages.
BEGIN;
ALTER TABLE memory_page ADD COLUMN IF NOT EXISTS entity_refs text;
ALTER TABLE memory_page ADD COLUMN IF NOT EXISTS page_type varchar(255);
ALTER TABLE memory_page ADD COLUMN IF NOT EXISTS page_timestamp timestamptz;
-- Historical summaries may mix entities; do not infer identities from their text.
UPDATE memory_page SET entity_refs = '[]' WHERE entity_refs IS NULL;
UPDATE memory_page SET page_type = 'EPISODIC' WHERE page_type IS NULL;
UPDATE memory_page SET page_timestamp = created_at WHERE page_timestamp IS NULL;
COMMIT;
