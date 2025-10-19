package ai.fitsum.cortex.api.dto;

import java.math.BigDecimal;
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
        String location,  // heading or page
        String snippet
    ) {}
}

