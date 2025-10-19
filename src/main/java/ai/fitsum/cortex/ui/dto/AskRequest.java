package ai.fitsum.cortex.ui.dto;

import java.util.List;

public record AskRequest(
    String question,
    List<String> sourceFilter,
    Boolean allowFallback,
    String sessionId
) {
}

