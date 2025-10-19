package ai.fitsum.cortex.ui.dto;

import java.util.List;

public record AskResponse(
    String answer,
    List<Citation> citations,
    String confidence,
    String sensitivity,
    String provider,
    String traceId,
    Integer latencyMs
) {
    public record Citation(
        Long chunkId,
        String documentTitle,
        String location,
        String snippet
    ) {}
}

