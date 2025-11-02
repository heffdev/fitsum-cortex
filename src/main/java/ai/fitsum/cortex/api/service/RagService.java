package ai.fitsum.cortex.api.service;

import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.dto.AskRequest;
import ai.fitsum.cortex.api.dto.AskResponse;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.retrieval.HybridRetriever;
import ai.fitsum.cortex.api.retrieval.RetrievedChunk;
import ai.fitsum.cortex.api.config.CortexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final HybridRetriever retriever;
    private final CortexProperties properties;
    private final String systemPrompt;
    
    public RagService(
        ChatClient.Builder chatClientBuilder,
        DocumentRepository documentRepository,
        HybridRetriever retriever,
        CortexProperties properties,
        ResourceLoader resourceLoader
    ) {
        this.documentRepository = documentRepository;
        this.retriever = retriever;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt(resourceLoader);
        this.chatClient = chatClientBuilder
            .defaultSystem(systemPrompt)
            .build();
    }
    
    public AskResponse ask(AskRequest request, String userId) {
        log.info("Processing question from user: {}", userId);
        
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        
        // Retrieve context and compose system message
        int topK = properties.getRetrieval().getRerankTopK();
        List<Long> docFilter = parseDocumentFilter(request.sourceFilter());
        List<RetrievedChunk> chunks = (docFilter != null && !docFilter.isEmpty())
            ? retriever.retrieve(request.question(), topK, docFilter)
            : retriever.retrieve(request.question(), topK);
        boolean hasContext = !chunks.isEmpty();
        if (!hasContext && !request.allowFallback() && !properties.getRetrieval().isAllowEmptyContext()) {
            throw new IllegalStateException("No relevant context found in knowledge base. Enable fallback mode to use general knowledge.");
        }
        String contextStr = buildContext(chunks);
        String enhancedSystem = systemPrompt + "\n\n# CONTEXT\n" + (hasContext ? contextStr : "No relevant context found in knowledge base.");

        String sessionId = request.sessionId() != null ? request.sessionId() : "default";
        String answer = chatClient.prompt()
            .advisors(a -> a
                .param("conversationId", sessionId)
                .param("sessionId", sessionId)
                .param("traceId", traceId)
            )
            .system(enhancedSystem)
            .user(request.question())
            .call()
            .content();
        
        List<AskResponse.Citation> citations = buildCitations(chunks);
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
        String sensitivity = "NONE";
        String provider = "LM_STUDIO";
        
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
        int topK = properties.getRetrieval().getRerankTopK();
        List<Long> docFilter = parseDocumentFilter(request.sourceFilter());
        List<RetrievedChunk> chunks = (docFilter != null && !docFilter.isEmpty())
            ? retriever.retrieve(request.question(), topK, docFilter)
            : retriever.retrieve(request.question(), topK);
        String contextStr = buildContext(chunks);
        String enhancedSystem = systemPrompt + "\n\n# CONTEXT\n" + (chunks.isEmpty() ? "No relevant context found in knowledge base." : contextStr);

        String sessionId = request.sessionId() != null ? request.sessionId() : "default";
        return chatClient.prompt()
            .advisors(a -> a
                .param("conversationId", sessionId)
                .param("sessionId", sessionId)
            )
            .system(enhancedSystem)
            .user(request.question())
            .stream()
            .content();
    }
    
    private List<Long> parseDocumentFilter(List<String> sourceFilter) {
        if (sourceFilter == null || sourceFilter.isEmpty()) return List.of();
        return sourceFilter.stream()
            .filter(s -> s != null && s.startsWith("doc:"))
            .map(s -> s.substring(4))
            .filter(idStr -> idStr.matches("\\d+"))
            .map(Long::valueOf)
            .distinct()
            .toList();
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
    
    private String buildContext(List<RetrievedChunk> chunks) {
        return chunks.stream()
            .map(rc -> {
                Document doc = documentRepository.findById(rc.chunk().documentId()).orElse(null);
                String title = doc != null ? doc.title() : "Unknown";
                String location = rc.chunk().heading() != null ? rc.chunk().heading() :
                                 rc.chunk().pageNumber() != null ? "Page " + rc.chunk().pageNumber() :
                                 "Section " + rc.chunk().chunkIndex();
                return String.format("""
                    [Document: %s, Location: %s]
                    %s
                    """, title, location, rc.chunk().content());
            })
            .collect(Collectors.joining("\n---\n"));
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

