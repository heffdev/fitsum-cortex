package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("eval_case")
public record EvalCase(
    @Id Long id,
    String category,
    String question,
    String expectedAnswer,
    Long[] expectedChunkIds,
    String sourceFilter,
    LocalDateTime createdAt
) {
}

