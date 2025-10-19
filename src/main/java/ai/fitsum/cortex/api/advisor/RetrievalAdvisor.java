package ai.fitsum.cortex.api.advisor;

import ai.fitsum.cortex.api.config.CortexProperties;
import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.retrieval.HybridRetriever;
import ai.fitsum.cortex.api.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performs hybrid retrieval and injects context into the prompt.
 * Enforces strict RAG: blocks empty-context answers unless explicitly allowed.
 */
@Component
public class RetrievalAdvisor implements CallAroundAdvisor {
    
    private static final Logger log = LoggerFactory.getLogger(RetrievalAdvisor.class);
    
    private final HybridRetriever retriever;
    private final DocumentRepository documentRepository;
    private final CortexProperties properties;
    
    public RetrievalAdvisor(
        HybridRetriever retriever,
        DocumentRepository documentRepository,
        CortexProperties properties
    ) {
        this.retriever = retriever;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }
    
    @Override
    public String getName() {
        return "Retrieval";
    }
    
    @Override
    public int getOrder() {
        return 300;  // After sensitivity classification
    }
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        String query = advisedRequest.userText();
        
        // Retrieve relevant chunks
        int topK = properties.getRetrieval().getRerankTopK();
        List<RetrievedChunk> chunks = retriever.retrieve(query, topK);
        
        log.info("Retrieved {} chunks for query", chunks.size());
        
        // Check if we have context
        boolean allowFallback = Boolean.TRUE.equals(advisedRequest.adviseContext().get("allowFallback"));
        boolean hasContext = !chunks.isEmpty();
        
        if (!hasContext && !allowFallback && !properties.getRetrieval().isAllowEmptyContext()) {
            throw new IllegalStateException(
                "No relevant context found in knowledge base. Enable fallback mode to use general knowledge."
            );
        }
        
        // Build context string with citations
        String contextStr = buildContext(chunks);
        
        // Store chunks in context for later citation building
        Map<String, Object> context = new HashMap<>(advisedRequest.adviseContext());
        context.put("retrievedChunks", chunks);
        context.put("contextProvided", hasContext);
        
        // Inject context into system message
        String enhancedSystemText = advisedRequest.systemText() + 
            "\n\n# CONTEXT\n" + 
            (hasContext ? contextStr : "No relevant context found in knowledge base.");
        
        AdvisedRequest updatedRequest = AdvisedRequest.from(advisedRequest)
            .withSystemText(enhancedSystemText)
            .withAdviseContext(context)
            .build();
        
        return chain.nextAroundCall(updatedRequest);
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
}

