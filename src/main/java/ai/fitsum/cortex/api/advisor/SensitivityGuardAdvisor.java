package ai.fitsum.cortex.api.advisor;

import ai.fitsum.cortex.api.config.CortexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies input sensitivity locally using regex patterns.
 * Privacy-first: if sensitive content detected, forces local model routing.
 */
@Component
public class SensitivityGuardAdvisor implements CallAroundAdvisor {
    
    private static final Logger log = LoggerFactory.getLogger(SensitivityGuardAdvisor.class);
    
    private final CortexProperties properties;
    private final Map<String, List<Pattern>> compiledPatterns;
    
    public SensitivityGuardAdvisor(CortexProperties properties) {
        this.properties = properties;
        this.compiledPatterns = compilePatterns();
    }
    
    @Override
    public String getName() {
        return "SensitivityGuard";
    }
    
    @Override
    public int getOrder() {
        return 200;  // After sanitization
    }
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        String userText = advisedRequest.userText();
        
        // Classify sensitivity
        SensitivityLevel level = classify(userText);
        
        log.info("Sensitivity classification: {}", level);
        
        // Store sensitivity in context for downstream advisors
        Map<String, Object> context = new HashMap<>(advisedRequest.adviseContext());
        context.put("sensitivity", level.name());
        
        AdvisedRequest updatedRequest = AdvisedRequest.from(advisedRequest)
            .withAdviseContext(context)
            .build();
        
        return chain.nextAroundCall(updatedRequest);
    }
    
    private SensitivityLevel classify(String text) {
        if (matches(text, "confidential")) {
            return SensitivityLevel.CONFIDENTIAL;
        }
        if (matches(text, "pii")) {
            return SensitivityLevel.PII;
        }
        return SensitivityLevel.NONE;
    }
    
    private boolean matches(String text, String category) {
        List<Pattern> patterns = compiledPatterns.get(category);
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }
    
    private Map<String, List<Pattern>> compilePatterns() {
        Map<String, List<Pattern>> compiled = new HashMap<>();
        
        Map<String, List<String>> patterns = properties.getSensitivity().getPatterns();
        if (patterns != null) {
            patterns.forEach((category, regexList) -> {
                List<Pattern> categoryPatterns = regexList.stream()
                    .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
                    .toList();
                compiled.put(category, categoryPatterns);
            });
        }
        
        return compiled;
    }
    
    public enum SensitivityLevel {
        NONE,
        PII,
        CONFIDENTIAL
    }
}

