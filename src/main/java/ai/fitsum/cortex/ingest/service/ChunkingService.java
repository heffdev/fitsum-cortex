package ai.fitsum.cortex.ingest.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chunks text into overlapping segments optimized for embedding and retrieval.
 * Strategy: 350-500 tokens per chunk, 15% overlap.
 */
@Service
public class ChunkingService {
    
    private static final int TARGET_CHUNK_SIZE = 400;  // tokens
    private static final int OVERLAP_PERCENT = 15;
    private static final double CHARS_PER_TOKEN = 4.0;  // rough approximation
    
    public List<TextChunk> chunk(String text, String documentTitle) {
        List<TextChunk> chunks = new ArrayList<>();
        
        // Split by paragraphs first to preserve semantic boundaries
        String[] paragraphs = text.split("\n\n+");
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        String currentHeading = null;
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // Detect headings (lines ending with : or starting with #)
            if (isHeading(paragraph)) {
                currentHeading = paragraph;
            }
            
            int estimatedTokens = estimateTokenCount(currentChunk.toString() + paragraph);
            
            if (estimatedTokens > TARGET_CHUNK_SIZE && currentChunk.length() > 0) {
                // Save current chunk
                chunks.add(new TextChunk(
                    chunkIndex++,
                    currentChunk.toString().trim(),
                    currentHeading,
                    null
                ));
                
                // Start new chunk with overlap
                String overlap = extractOverlap(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }
            
            currentChunk.append(paragraph).append("\n\n");
        }
        
        // Add final chunk
        if (currentChunk.length() > 0) {
            chunks.add(new TextChunk(
                chunkIndex,
                currentChunk.toString().trim(),
                currentHeading,
                null
            ));
        }
        
        return chunks;
    }
    
    private boolean isHeading(String text) {
        return text.startsWith("#") || 
               (text.length() < 100 && text.endsWith(":")) ||
               text.matches("^[A-Z][A-Za-z\\s]{2,50}$");
    }
    
    private int estimateTokenCount(String text) {
        return (int) (text.length() / CHARS_PER_TOKEN);
    }
    
    private String extractOverlap(String text) {
        int overlapSize = (int) (text.length() * OVERLAP_PERCENT / 100.0);
        if (overlapSize == 0 || text.length() < overlapSize) {
            return "";
        }
        
        // Extract from end, but try to break at sentence boundary
        String overlap = text.substring(text.length() - overlapSize);
        
        // Find first sentence boundary
        Pattern sentenceEnd = Pattern.compile("[.!?]\\s+");
        Matcher matcher = sentenceEnd.matcher(overlap);
        if (matcher.find()) {
            return overlap.substring(matcher.end());
        }
        
        return overlap;
    }
    
    public record TextChunk(
        int index,
        String content,
        String heading,
        Integer pageNumber
    ) {}
}

