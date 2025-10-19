package ai.fitsum.cortex.api.advisor;

import ai.fitsum.cortex.api.domain.QaLog;
import ai.fitsum.cortex.api.repository.QaLogRepository;
import ai.fitsum.cortex.api.retrieval.RetrievedChunk;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Captures telemetry and logs all Q&A interactions.
 * Emits OpenTelemetry spans and persists to qa_log table.
 * Only active when observability is enabled.
 */
@Component
@ConditionalOnProperty(name = "cortex.observability.enabled", havingValue = "true")
public class TelemetryAdvisor implements CallAroundAdvisor {
    
    private static final Logger log = LoggerFactory.getLogger(TelemetryAdvisor.class);
    
    private final QaLogRepository qaLogRepository;
    private final ObservationRegistry observationRegistry;
    
    public TelemetryAdvisor(QaLogRepository qaLogRepository, ObservationRegistry observationRegistry) {
        this.qaLogRepository = qaLogRepository;
        this.observationRegistry = observationRegistry;
    }
    
    @Override
    public String getName() {
        return "Telemetry";
    }
    
    @Override
    public int getOrder() {
        return 900;  // Near end of chain
    }
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        Observation observation = Observation.createNotStarted("cortex.ask", observationRegistry)
            .lowCardinalityKeyValue("provider", 
                (String) advisedRequest.adviseContext().getOrDefault("provider", "OPENAI"))
            .lowCardinalityKeyValue("sensitivity", 
                (String) advisedRequest.adviseContext().getOrDefault("sensitivity", "NONE"))
            .start();
        
        try {
            AdvisedResponse response = chain.nextAroundCall(advisedRequest);
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Extract metadata
            ChatResponseMetadata metadata = response.response().getMetadata();
            Integer tokensPrompt = metadata != null && metadata.getUsage() != null ? 
                metadata.getUsage().getPromptTokens().intValue() : null;
            Integer tokensCompletion = metadata != null && metadata.getUsage() != null ? 
                metadata.getUsage().getGenerationTokens().intValue() : null;
            
            // Extract chunk IDs
            @SuppressWarnings("unchecked")
            List<RetrievedChunk> chunks = (List<RetrievedChunk>) 
                advisedRequest.adviseContext().get("retrievedChunks");
            Long[] chunkIds = chunks != null ? 
                chunks.stream().map(rc -> rc.chunk().id()).toArray(Long[]::new) : 
                new Long[0];
            
            // Log to database
            QaLog qaLog = QaLog.create(
                (String) advisedRequest.adviseContext().getOrDefault("userId", "anonymous"),
                (String) advisedRequest.adviseContext().getOrDefault("sessionId", "default"),
                advisedRequest.userText(),
                response.response().getResult().getOutput().getContent(),
                (String) advisedRequest.adviseContext().getOrDefault("provider", "OPENAI"),
                (String) advisedRequest.adviseContext().getOrDefault("sensitivity", "NONE"),
                chunkIds,
                null,  // confidence - would be extracted from structured output
                Boolean.TRUE.equals(advisedRequest.adviseContext().get("allowFallback")),
                traceId,
                (int) latency,
                tokensPrompt,
                tokensCompletion,
                calculateCost(tokensPrompt, tokensCompletion)
            );
            
            qaLogRepository.save(qaLog);
            
            observation.highCardinalityKeyValue("trace.id", traceId);
            observation.highCardinalityKeyValue("latency.ms", String.valueOf(latency));
            observation.stop();
            
            log.info("Q&A logged: traceId={}, latency={}ms, chunks={}", 
                traceId, latency, chunkIds.length);
            
            return response;
            
        } catch (Exception e) {
            observation.error(e);
            observation.stop();
            throw e;
        }
    }
    
    private BigDecimal calculateCost(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null || completionTokens == null) {
            return BigDecimal.ZERO;
        }
        
        // GPT-4o pricing (as of 2024): $5/1M input, $15/1M output
        double inputCost = (promptTokens / 1_000_000.0) * 5.0;
        double outputCost = (completionTokens / 1_000_000.0) * 15.0;
        
        return BigDecimal.valueOf(inputCost + outputCost);
    }
}

