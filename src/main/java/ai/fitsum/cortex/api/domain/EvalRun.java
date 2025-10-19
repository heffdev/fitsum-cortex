package ai.fitsum.cortex.api.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("eval_run")
public record EvalRun(
    @Id Long id,
    JsonNode configJson,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    BigDecimal precisionAt5,
    BigDecimal mrr,
    BigDecimal faithfulnessScore
) {
}

