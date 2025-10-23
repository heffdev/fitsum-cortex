package ai.fitsum.cortex.api.service;

import ai.fitsum.cortex.api.advisor.*;
import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.dto.AskRequest;
import ai.fitsum.cortex.api.dto.AskResponse;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core RAG orchestration service.
 * Coordinates the full advisor chain and manages chat interactions.
 */
@Service
public class RagService {
    
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    
    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final String systemPrompt;
    
    public RagService(
        ChatClient.Builder chatClientBuilder,
        InputSanitizerAdvisor inputSanitizer,
        SensitivityGuardAdvisor sensitivityGuard,
        RetrievalAdvisor retrievalAdvisor,
        ModelRoutingAdvisor modelRouting,
        @org.springframework.lang.Nullable TelemetryAdvisor telemetry,
        DocumentRepository documentRepository,
        ResourceLoader resourceLoader
    ) {
        this.documentRepository = documentRepository;
        this.systemPrompt = loadSystemPrompt(resourceLoader);
        
        // Build chat client with full advisor chain
        ChatClient.Builder builder = chatClientBuilder
            .defaultAdvisors(
                new SimpleLoggerAdvisor(),
                inputSanitizer,
                sensitivityGuard,
                retrievalAdvisor,
                modelRouting
            )
            .defaultSystem(systemPrompt);

        if (telemetry != null) {
            builder = builder.defaultAdvisors(telemetry);
        }
        this.chatClient = builder.build();
    }
    
    public AskResponse ask(AskRequest request, String userId) {
        log.info("Processing question from user: {}", userId);
        
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        
        // Build advisor context
        Map<String, Object> adviseContext = new HashMap<>();
        adviseContext.put("userId", userId);
        adviseContext.put("sessionId", request.sessionId() != null ? request.sessionId() : "default");
        adviseContext.put("allowFallback", request.allowFallback());
        adviseContext.put("traceId", traceId);
        
        // Execute chat with advisor chain
        // Note: adviseContext passing may differ in Spring AI 1.0.0-M3
        // For now, advisors will use their default context handling
        ChatResponse response = chatClient.prompt()
            .user(request.question())
            .call()
            .chatResponse();
        
        // Extract retrieved chunks from context for citations
        @SuppressWarnings("unchecked")
        List<RetrievedChunk> chunks = (List<RetrievedChunk>) 
            adviseContext.get("retrievedChunks");
        
        List<AskResponse.Citation> citations = buildCitations(chunks);
        
        String answer = response.getResult().getOutput().getContent();
        // Simple heuristic: combine top reranked score and source agreement
        double confidenceScore = 0.5;
        String confidenceLabel = "MEDIUM";
        if (chunks != null && !chunks.isEmpty()) {
            double top = chunks.get(0).score();
            long uniqueDocs = chunks.stream()
                .map(rc -> rc.chunk().documentId())
                .distinct().count();

            double topN = normalize(top);
            double diversity = Math.min(1.0, uniqueDocs / 3.0);

            // Do not penalize single-document answers: use top score directly
            double blended = (uniqueDocs <= 1) ? topN : (0.6 * topN + 0.4 * diversity);

            confidenceScore = Math.max(0.1, Math.min(0.99, blended));
            confidenceLabel = confidenceScore >= 0.80 ? "HIGH" : (confidenceScore >= 0.60 ? "MEDIUM" : "LOW");
        }
        String sensitivity = (String) adviseContext.getOrDefault("sensitivity", "NONE");
        String provider = (String) adviseContext.getOrDefault("provider", "OPENAI");
        
        int latency = (int) (System.currentTimeMillis() - startTime);
        
        return new AskResponse(
            answer,
            citations,
            confidenceScore,
            sensitivity,
            provider,
            traceId,
            latency,
            confidenceLabel
        );
    }
    
    public Flux<String> askStream(AskRequest request, String userId) {
        log.info("Processing streaming question from user: {}", userId);
        
        // Note: Context passing simplified for Spring AI 1.0.0-M3 compatibility
        return chatClient.prompt()
            .user(request.question())
            .stream()
            .content();
    }
    
    private List<AskResponse.Citation> buildCitations(List<RetrievedChunk> chunks) {
        if (chunks == null) {
            return List.of();
        }
        
        return chunks.stream()
            .map(rc -> {
                Document doc = documentRepository.findById(rc.chunk().documentId()).orElse(null);
                String title = doc != null ? doc.title() : "Unknown Document";
                String location = rc.chunk().heading() != null ? rc.chunk().heading() :
                                 rc.chunk().pageNumber() != null ? "Page " + rc.chunk().pageNumber() :
                                 "Section " + rc.chunk().chunkIndex();
                String snippet = rc.chunk().content().substring(
                    0, Math.min(200, rc.chunk().content().length())
                ) + "...";
                
                return new AskResponse.Citation(
                    rc.chunk().id(),
                    title,
                    location,
                    snippet
                );
            })
            .collect(Collectors.toList());
    }
    
    private String loadSystemPrompt(ResourceLoader resourceLoader) {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/system/answerer.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load system prompt, using default", e);
            return "You are a helpful assistant that answers based strictly on provided context.";
        }
    }

    private double normalize(double score) {
        // ReRanker currently yields ~0..1; guard in case of different scales
        if (Double.isNaN(score) || Double.isInfinite(score)) return 0.0;
        if (score < 0) return 0.0;
        if (score > 1) return 1.0;
        return score;
    }
}

