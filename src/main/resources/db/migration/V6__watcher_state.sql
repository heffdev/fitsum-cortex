CREATE TABLE IF NOT EXISTS watcher_state (
    path TEXT PRIMARY KEY,
    last_modified BIGINT,
    size BIGINT,
    content_hash TEXT
);

