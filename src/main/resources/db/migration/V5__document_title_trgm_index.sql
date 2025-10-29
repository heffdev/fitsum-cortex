-- Add trigram index on document.title to improve fuzzy title matching
CREATE INDEX IF NOT EXISTS idx_document_title_trgm
  ON document USING gin (title gin_trgm_ops);

