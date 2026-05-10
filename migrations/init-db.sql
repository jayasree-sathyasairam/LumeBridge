CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE IF NOT EXISTS tasks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payload_hash    TEXT UNIQUE NOT NULL,
    payload         TEXT,
    result          JSONB,
    status          TEXT NOT NULL DEFAULT 'pending',
    version         INT NOT NULL DEFAULT 1,
    response_hash   TEXT,
    embedding       vector(1536),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tasks_payload_hash ON tasks (payload_hash);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks (status);

-- Step 5: semantic cache (pgvector cosine search)
CREATE TABLE IF NOT EXISTS semantic_cache_entries (
    cache_key         TEXT PRIMARY KEY,
    embedding         vector(64) NOT NULL,
    cached_response   JSONB NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
