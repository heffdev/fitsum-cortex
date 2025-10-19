-- Ensure embedding vector dimension matches the embedding model (384)
-- Rebuild the IVFFLAT index because type change requires reindexing
DROP INDEX IF EXISTS idx_chunk_embedding;

ALTER TABLE chunk
    ALTER COLUMN embedding TYPE vector(384);

CREATE INDEX idx_chunk_embedding
    ON chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);


