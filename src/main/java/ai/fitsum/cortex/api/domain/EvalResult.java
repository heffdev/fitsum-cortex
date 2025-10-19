package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("eval_result")
public record EvalResult(
    @Id Long id,
    Long runId,
    Long caseId,
    Long[] retrievedChunkIds,
    String answer,
    BigDecimal precision,
    BigDecimal reciprocalRank,
    Boolean isFaithful,
    LocalDateTime createdAt
) {
}

