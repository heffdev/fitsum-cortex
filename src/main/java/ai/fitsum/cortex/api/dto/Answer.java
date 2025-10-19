package ai.fitsum.cortex.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured output schema for LLM answers.
 * This matches the JSON schema validation expected from the model.
 */
public record Answer(
    String answer,
    List<CitedFact> citedFacts,
    String confidence,
    String reasoning
) {
    public record CitedFact(
        String claim,
        List<Integer> chunkIndices  // 0-based indices into the provided context
    ) {}
    
    public enum Confidence {
        HIGH, MEDIUM, LOW
    }
}

