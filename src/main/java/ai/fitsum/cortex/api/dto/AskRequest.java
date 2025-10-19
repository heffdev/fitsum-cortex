package ai.fitsum.cortex.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AskRequest(
    @NotBlank(message = "Question cannot be empty")
    @Size(max = 2000, message = "Question too long")
    String question,
    
    List<String> sourceFilter,
    Boolean allowFallback,
    String sessionId
) {
    public AskRequest {
        if (allowFallback == null) {
            allowFallback = false;
        }
    }
}

