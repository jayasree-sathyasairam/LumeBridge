-- V2 P0 upgrade for existing Postgres volumes (run manually against lumebridge_nexus).
-- Adds tenant+freshness scope, composite primary key, and HNSW cosine index.

BEGIN;

ALTER TABLE semantic_cache_entries ADD COLUMN IF NOT EXISTS scope TEXT;

UPDATE semantic_cache_entries SET scope = 'legacy_global:TIME_UNSPECIFIED' WHERE scope IS NULL OR scope = '';

ALTER TABLE semantic_cache_entries ALTER COLUMN scope SET NOT NULL;

ALTER TABLE semantic_cache_entries DROP CONSTRAINT IF EXISTS semantic_cache_entries_pkey;

ALTER TABLE semantic_cache_entries ADD PRIMARY KEY (scope, cache_key);

CREATE INDEX IF NOT EXISTS idx_semantic_scope ON semantic_cache_entries (scope);

CREATE INDEX IF NOT EXISTS idx_semantic_hnsw
    ON semantic_cache_entries USING hnsw (embedding vector_cosine_ops);

ALTER TABLE semantic_cache_entries ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NULL;

COMMIT;
