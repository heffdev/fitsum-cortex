package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("chunk")
public record Chunk(
    @Id Long id,
    Long documentId,
    Integer chunkIndex,
    String content,
    String contentHash,
    Integer tokenCount,
    String heading,
    Integer pageNumber,
    float[] embedding,
    LocalDateTime createdAt
) {
    public static Chunk create(
        Long documentId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer tokenCount,
        String heading,
        Integer pageNumber,
        float[] embedding
    ) {
        return new Chunk(
            null,
            documentId,
            chunkIndex,
            content,
            contentHash,
            tokenCount,
            heading,
            pageNumber,
            embedding,
            LocalDateTime.now()
        );
    }
}

