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

-- V2 P0: semantic cache — scoped rows + ANN index (pgvector HNSW cosine)
CREATE TABLE IF NOT EXISTS semantic_cache_entries (
    scope             TEXT NOT NULL,
    cache_key         TEXT NOT NULL,
    embedding         vector(64) NOT NULL,
    cached_response   JSONB NOT NULL,
    expires_at        TIMESTAMPTZ NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, cache_key)
);

CREATE INDEX IF NOT EXISTS idx_semantic_scope ON semantic_cache_entries (scope);

CREATE INDEX IF NOT EXISTS idx_semantic_hnsw
    ON semantic_cache_entries USING hnsw (embedding vector_cosine_ops);
