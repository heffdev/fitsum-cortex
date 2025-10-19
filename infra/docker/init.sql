-- Initialize pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE cortex TO cortex;

