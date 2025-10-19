package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("document")
public record Document(
    @Id Long id,
    Long sourceId,
    String externalId,
    String title,
    String contentHash,
    String contentType,
    String rawContent,
    String metadataJson,
    LocalDateTime indexedAt,
    LocalDateTime updatedAt,
    LocalDateTime createdAt
) {
    public static Document create(
        Long sourceId,
        String externalId,
        String title,
        String contentHash,
        String contentType,
        String rawContent,
        String metadata
    ) {
        return new Document(
            null,
            sourceId,
            externalId,
            title,
            contentHash,
            contentType,
            rawContent,
            metadata,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
}

