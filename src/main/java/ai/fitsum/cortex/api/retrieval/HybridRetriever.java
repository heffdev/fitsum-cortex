package ai.fitsum.cortex.api.retrieval;

import ai.fitsum.cortex.api.config.CortexProperties;
import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval combining Full-Text Search (FTS) and Approximate Nearest Neighbor (ANN) search,
 * followed by reranking to optimize for quality over quantity.
 * 
 * Strategy: FTS + ANN union → Rerank → Keep top K
 */
@Service
public class HybridRetriever {
    
    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    
    private final ChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final ReRanker reRanker;
    private final CortexProperties properties;
    
    public HybridRetriever(
        ChunkRepository chunkRepository,
        @Qualifier("cortexEmbeddingModel") EmbeddingModel embeddingModel,
        ReRanker reRanker,
        CortexProperties properties
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.reRanker = reRanker;
        this.properties = properties;
    }
    
    public List<RetrievedChunk> retrieve(String query, int topK) {
        log.debug("Hybrid retrieval for query: {}", query);
        
        // Step 1: Full-Text Search
        List<Chunk> ftsResults = chunkRepository.fullTextSearch(
            query, 
            properties.getRetrieval().getFtsTopK()
        );
        log.debug("FTS returned {} chunks", ftsResults.size());
        
        // Step 2: Vector Search (ANN)
        float[] queryEmbedding = embeddingModel.embed(query);
        String embeddingStr = formatEmbeddingForPostgres(queryEmbedding);
        List<Chunk> annResults = chunkRepository.vectorSearch(
            embeddingStr,
            properties.getRetrieval().getAnnTopK()
        );
        log.debug("ANN returned {} chunks", annResults.size());
        
        // Step 3: Union and deduplicate
        Set<Long> seenIds = new HashSet<>();
        List<Chunk> candidateChunks = new ArrayList<>();
        
        for (Chunk chunk : ftsResults) {
            if (seenIds.add(chunk.id())) {
                candidateChunks.add(chunk);
            }
        }
        for (Chunk chunk : annResults) {
            if (seenIds.add(chunk.id())) {
                candidateChunks.add(chunk);
            }
        }
        
        log.debug("Union of FTS+ANN: {} unique chunks", candidateChunks.size());
        
        // Step 4: Rerank
        List<RetrievedChunk> reranked = reRanker.rerank(query, candidateChunks);
        
        // Step 5: Keep top K, never exceed max
        int limit = Math.min(topK, properties.getRetrieval().getMaxChunks());
        List<RetrievedChunk> finalResults = reranked.stream()
            .limit(limit)
            .collect(Collectors.toList());
        
        log.info("Hybrid retrieval returned {} chunks (limit: {})", finalResults.size(), limit);
        return finalResults;
    }
    
    public List<RetrievedChunk> retrieve(String query, int topK, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return retrieve(query, topK);
        }
        log.debug("Hybrid retrieval (filtered) for query: {} on documents {}", query, documentIds);
        Long[] idsArray = documentIds.toArray(Long[]::new);
        // FTS filtered
        List<Chunk> ftsResults = chunkRepository.fullTextSearchByDocuments(
            query,
            idsArray,
            properties.getRetrieval().getFtsTopK()
        );
        // ANN filtered
        float[] queryEmbedding = embeddingModel.embed(query);
        String embeddingStr = formatEmbeddingForPostgres(queryEmbedding);
        List<Chunk> annResults = chunkRepository.vectorSearchByDocuments(
            embeddingStr,
            idsArray,
            properties.getRetrieval().getAnnTopK()
        );
        // Union
        Set<Long> seenIds = new HashSet<>();
        List<Chunk> candidateChunks = new ArrayList<>();
        for (Chunk c : ftsResults) if (seenIds.add(c.id())) candidateChunks.add(c);
        for (Chunk c : annResults) if (seenIds.add(c.id())) candidateChunks.add(c);
        // Rerank and cap
        List<RetrievedChunk> reranked = reRanker.rerank(query, candidateChunks);
        int limit = Math.min(topK, properties.getRetrieval().getMaxChunks());
        return reranked.stream().limit(limit).collect(Collectors.toList());
    }
    
    private String formatEmbeddingForPostgres(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

