package ai.fitsum.cortex.api.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sanitizes input to prevent prompt injection and validates basic constraints.
 */
@Component
public class InputSanitizerAdvisor implements CallAroundAdvisor {
    
    private static final Logger log = LoggerFactory.getLogger(InputSanitizerAdvisor.class);
    private static final int MAX_QUESTION_LENGTH = 2000;
    
    @Override
    public String getName() {
        return "InputSanitizer";
    }
    
    @Override
    public int getOrder() {
        return 100;  // First in chain
    }
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        String userText = advisedRequest.userText();
        
        // Validate length
        if (userText.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question exceeds maximum length of " + MAX_QUESTION_LENGTH);
        }
        
        // Detect potential prompt injection patterns
        String lowerText = userText.toLowerCase();
        if (lowerText.contains("ignore previous") || 
            lowerText.contains("disregard") && lowerText.contains("instruction") ||
            lowerText.contains("system prompt")) {
            log.warn("Potential prompt injection detected: {}", userText.substring(0, Math.min(50, userText.length())));
            // Could reject or sanitize - for now just log and proceed
        }
        
        // Basic sanitization: trim and normalize whitespace
        String sanitized = userText.trim().replaceAll("\\s+", " ");
        
        log.debug("Input sanitized, length: {} -> {}", userText.length(), sanitized.length());
        
        // Update request with sanitized input
        AdvisedRequest sanitizedRequest = AdvisedRequest.from(advisedRequest)
            .withUserText(sanitized)
            .build();
        
        return chain.nextAroundCall(sanitizedRequest);
    }
}

