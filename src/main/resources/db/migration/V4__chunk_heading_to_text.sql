-- Allow long headings extracted during chunking
ALTER TABLE chunk
    ALTER COLUMN heading TYPE TEXT;


