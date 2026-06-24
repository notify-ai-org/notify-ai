-- Required when schema management is disabled (DDL_AUTO=none or validate).
-- Stores SQL-backed episodic-memory pages used by the event processor.
CREATE TABLE IF NOT EXISTS memory_page (
    page_id VARCHAR(255) NOT NULL,
    namespace VARCHAR(255),
    correlation_id VARCHAR(255),
    summary TEXT,
    severity_max VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    embedding BYTEA,
    PRIMARY KEY (page_id)
);

CREATE INDEX IF NOT EXISTS idx_memory_page_namespace
    ON memory_page (namespace);

CREATE INDEX IF NOT EXISTS idx_memory_page_correlation_id
    ON memory_page (correlation_id);
