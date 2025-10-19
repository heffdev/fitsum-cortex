-- Convert JSONB metadata/config columns to TEXT to simplify JDBC mapping
ALTER TABLE document
    ALTER COLUMN metadata_json TYPE TEXT USING metadata_json::text;

ALTER TABLE source
    ALTER COLUMN config_json TYPE TEXT USING config_json::text;


