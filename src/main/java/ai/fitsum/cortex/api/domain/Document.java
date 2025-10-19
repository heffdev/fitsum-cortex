package ai.fitsum.cortex.api.domain;

import com.fasterxml.jackson.databind.JsonNode;
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
    JsonNode metadataJson,
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
        JsonNode metadata
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

