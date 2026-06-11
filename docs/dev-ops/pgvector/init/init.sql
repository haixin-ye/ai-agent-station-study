CREATE EXTENSION IF NOT EXISTS vector;

DROP TABLE IF EXISTS public.store_openai;
DROP TABLE IF EXISTS public.vector_store_openai;

CREATE TABLE public.store_openai (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1536)
);

CREATE TABLE public.vector_store_openai (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1536)
);

DROP TABLE IF EXISTS public.vec_turn_summary;
DROP TABLE IF EXISTS public.vec_conversation_summary;
DROP TABLE IF EXISTS public.vec_long_term_memory;
DROP TABLE IF EXISTS public.vec_user_preference;
DROP TABLE IF EXISTS public.vec_artifact_summary;
DROP TABLE IF EXISTS public.vec_artifact_chunk;
DROP TABLE IF EXISTS public.vec_rag_document;
DROP TABLE IF EXISTS public.vec_rag_chunk;
DROP TABLE IF EXISTS public.vec_rag_file_chunk;
DROP TABLE IF EXISTS public.vec_rag_code_file_summary;
DROP TABLE IF EXISTS public.vec_rag_code_chunk;

CREATE TABLE public.vec_turn_summary (
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

CREATE TABLE public.vec_conversation_summary (
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

CREATE TABLE public.vec_long_term_memory (
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

CREATE TABLE public.vec_user_preference (
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

CREATE TABLE public.vec_artifact_summary (
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

CREATE TABLE public.vec_artifact_chunk (
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

CREATE TABLE public.vec_rag_document (
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

CREATE TABLE public.vec_rag_chunk (
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

CREATE TABLE public.vec_rag_file_chunk (
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

CREATE TABLE public.vec_rag_code_file_summary (
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

CREATE TABLE public.vec_rag_code_chunk (
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

CREATE INDEX idx_vec_turn_summary_scope ON public.vec_turn_summary (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_conversation_summary_scope ON public.vec_conversation_summary (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_long_term_memory_scope ON public.vec_long_term_memory (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_user_preference_scope ON public.vec_user_preference (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_artifact_summary_scope ON public.vec_artifact_summary (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_artifact_chunk_scope ON public.vec_artifact_chunk (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_rag_document_scope ON public.vec_rag_document (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_rag_chunk_scope ON public.vec_rag_chunk (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_rag_file_chunk_scope ON public.vec_rag_file_chunk (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_rag_code_file_summary_scope ON public.vec_rag_code_file_summary (user_id, session_id, occurred_at);
CREATE INDEX idx_vec_rag_code_chunk_scope ON public.vec_rag_code_chunk (user_id, session_id, occurred_at);
