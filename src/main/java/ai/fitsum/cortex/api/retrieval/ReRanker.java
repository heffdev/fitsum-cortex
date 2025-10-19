package ai.fitsum.cortex.api.retrieval;

import ai.fitsum.cortex.api.domain.Chunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reranks chunks using a simple scoring heuristic.
 * In production, this would use a cross-encoder model for better accuracy.
 * 
 * Current implementation uses a simple lexical overlap + BM25-style scoring.
 */
@Service
public class ReRanker {
    
    public List<RetrievedChunk> rerank(String query, List<Chunk> chunks) {
        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        return chunks.stream()
            .map(chunk -> {
                double score = calculateScore(queryTerms, chunk.content());
                return RetrievedChunk.of(chunk, score, "reranked");
            })
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .collect(Collectors.toList());
    }
    
    private double calculateScore(String[] queryTerms, String content) {
        String contentLower = content.toLowerCase();
        
        // Simple term frequency scoring
        int matches = 0;
        for (String term : queryTerms) {
            if (contentLower.contains(term)) {
                matches++;
            }
        }
        
        // Normalize by query length
        double termCoverage = (double) matches / queryTerms.length;
        
        // Bonus for exact phrase match
        String queryPhrase = String.join(" ", queryTerms);
        double phraseBonus = contentLower.contains(queryPhrase) ? 0.3 : 0.0;
        
        // Length penalty (prefer concise, relevant chunks)
        double lengthPenalty = 1.0 / (1.0 + Math.log(content.length() / 100.0));
        
        return (termCoverage + phraseBonus) * lengthPenalty;
    }
}

