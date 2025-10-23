-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- For text search

-- Source table: tracks configured knowledge sources
CREATE TABLE source (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,  -- LOCAL_FILES, GMAIL, etc.
    config_json TEXT,  -- serialized config (OAuth tokens, paths, etc.)
    is_enabled BOOLEAN DEFAULT true,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_source_type ON source(source_type);
CREATE INDEX idx_source_enabled ON source(is_enabled);

-- Document table: original documents from sources
CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    external_id VARCHAR(512),  -- source-specific ID
    title VARCHAR(1024),
    content_hash VARCHAR(64) NOT NULL,  -- SHA-256 for dedupe
    content_type VARCHAR(100),
    raw_content TEXT,
    metadata_json TEXT,  -- serialized metadata (author, date, labels, etc.)
    indexed_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_document_content_hash ON document(source_id, content_hash);
CREATE INDEX idx_document_source ON document(source_id);
CREATE INDEX idx_document_external_id ON document(source_id, external_id);
CREATE INDEX idx_document_updated ON document(updated_at);

-- Chunk table: text chunks with embeddings for retrieval
CREATE TABLE chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    token_count INTEGER,
    heading TEXT,  -- extracted heading/section
    page_number INTEGER,
    embedding vector(1024),  -- BGE large v1.5 (1024-dim)
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_chunk_document ON chunk(document_id);
CREATE INDEX idx_chunk_embedding ON chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_chunk_content_trgm ON chunk USING gin (content gin_trgm_ops);  -- trigram text search
CREATE INDEX idx_chunk_content_fts ON chunk USING gin (to_tsvector('english', content));  -- FTS index
CREATE INDEX idx_chunk_hash ON chunk(content_hash);

-- QA Log: audit trail of all questions and answers
CREATE TABLE qa_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    session_id VARCHAR(255),
    question TEXT NOT NULL,
    answer TEXT,
    provider VARCHAR(50),  -- OPENAI, OLLAMA, etc.
    sensitivity VARCHAR(20),  -- NONE, PII, CONFIDENTIAL, etc.
    chunk_ids BIGINT[],  -- chunks used for context
    confidence_score DECIMAL(3,2),
    allow_fallback BOOLEAN DEFAULT false,
    trace_id VARCHAR(255),
    latency_ms INTEGER,
    tokens_prompt INTEGER,
    tokens_completion INTEGER,
    cost_usd DECIMAL(10,6),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_qa_log_user ON qa_log(user_id);
CREATE INDEX idx_qa_log_session ON qa_log(session_id);
CREATE INDEX idx_qa_log_trace ON qa_log(trace_id);
CREATE INDEX idx_qa_log_created ON qa_log(created_at);

-- Eval Case: golden test cases for retrieval quality
CREATE TABLE eval_case (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(100),  -- FACTUAL, REASONING, MULTI_HOP, etc.
    question TEXT NOT NULL,
    expected_answer TEXT,
    expected_chunk_ids BIGINT[],  -- ideal chunks to retrieve
    source_filter VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Eval Run: snapshot of an evaluation run
CREATE TABLE eval_run (
    id BIGSERIAL PRIMARY KEY,
    config_json JSONB,  -- retrieval params used
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    precision_at_5 DECIMAL(5,4),
    mrr DECIMAL(5,4),
    faithfulness_score DECIMAL(5,4)
);

-- Eval Result: individual case results from a run
CREATE TABLE eval_result (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES eval_run(id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES eval_case(id),
    retrieved_chunk_ids BIGINT[],
    answer TEXT,
    precision DECIMAL(5,4),
    reciprocal_rank DECIMAL(5,4),
    is_faithful BOOLEAN,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_eval_result_run ON eval_result(run_id);
CREATE INDEX idx_eval_result_case ON eval_result(case_id);

