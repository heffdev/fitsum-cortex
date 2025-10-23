package ai.fitsum.cortex.api.dto;

import java.util.List;

public record AskResponse(
    String answer,
    List<Citation> citations,
    double confidence,
    String sensitivity,
    String provider,
    String traceId,
    Integer latencyMs,
    String confidenceLabel
) {
    public record Citation(
        Long chunkId,
        String documentTitle,
        String location,  // heading or page
        String snippet
    ) {}
}

