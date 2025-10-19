package ai.fitsum.cortex.api.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("source")
public record Source(
    @Id Long id,
    String name,
    SourceType sourceType,
    JsonNode configJson,
    Boolean isEnabled,
    LocalDateTime lastSyncAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public enum SourceType {
        LOCAL_FILES,
        GMAIL
    }
    
    public static Source create(String name, SourceType sourceType, JsonNode configJson) {
        return new Source(
            null,
            name,
            sourceType,
            configJson,
            true,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    public Source withLastSyncAt(LocalDateTime lastSyncAt) {
        return new Source(id, name, sourceType, configJson, isEnabled, lastSyncAt, createdAt, LocalDateTime.now());
    }
}

