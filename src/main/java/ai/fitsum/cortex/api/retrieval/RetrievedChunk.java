package ai.fitsum.cortex.api.retrieval;

import ai.fitsum.cortex.api.domain.Chunk;

/**
 * A chunk with its retrieval score for ranking purposes.
 */
public record RetrievedChunk(
    Chunk chunk,
    double score,
    String source  // "fts", "ann", or "hybrid"
) {
    public static RetrievedChunk of(Chunk chunk, double score, String source) {
        return new RetrievedChunk(chunk, score, source);
    }
}

