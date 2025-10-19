package ai.fitsum.cortex.ingest.service;

import java.time.Instant;

public record IngestionResult(
    Long documentId,
    String title,
    String filename,
    long sizeBytes,
    Instant uploadedAt,
    boolean duplicate
) {}


