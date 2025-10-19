package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("qa_log")
public record QaLog(
    @Id Long id,
    String userId,
    String sessionId,
    String question,
    String answer,
    String provider,
    String sensitivity,
    Long[] chunkIds,
    BigDecimal confidenceScore,
    Boolean allowFallback,
    String traceId,
    Integer latencyMs,
    Integer tokensPrompt,
    Integer tokensCompletion,
    BigDecimal costUsd,
    LocalDateTime createdAt
) {
    public static QaLog create(
        String userId,
        String sessionId,
        String question,
        String answer,
        String provider,
        String sensitivity,
        Long[] chunkIds,
        BigDecimal confidenceScore,
        Boolean allowFallback,
        String traceId,
        Integer latencyMs,
        Integer tokensPrompt,
        Integer tokensCompletion,
        BigDecimal costUsd
    ) {
        return new QaLog(
            null,
            userId,
            sessionId,
            question,
            answer,
            provider,
            sensitivity,
            chunkIds,
            confidenceScore,
            allowFallback,
            traceId,
            latencyMs,
            tokensPrompt,
            tokensCompletion,
            costUsd,
            LocalDateTime.now()
        );
    }
}

