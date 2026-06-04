-- AutoAgent RAG vector migration.
-- Safe for existing pgvector databases: creates new RAG vector collections if they do not exist.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS public.vec_rag_file_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    content TEXT NOT NULL,
    summary TEXT,
    metadata JSONB,
    occurred_at TIMESTAMP,
    embedding VECTOR(1536),
    UNIQUE (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS public.vec_rag_code_file_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    content TEXT NOT NULL,
    summary TEXT,
    metadata JSONB,
    occurred_at TIMESTAMP,
    embedding VECTOR(1536),
    UNIQUE (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS public.vec_rag_code_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    content TEXT NOT NULL,
    summary TEXT,
    metadata JSONB,
    occurred_at TIMESTAMP,
    embedding VECTOR(1536),
    UNIQUE (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_vec_rag_file_chunk_scope
    ON public.vec_rag_file_chunk (user_id, session_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_vec_rag_code_file_summary_scope
    ON public.vec_rag_code_file_summary (user_id, session_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_vec_rag_code_chunk_scope
    ON public.vec_rag_code_chunk (user_id, session_id, occurred_at);
