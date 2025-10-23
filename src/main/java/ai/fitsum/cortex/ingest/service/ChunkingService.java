package ai.fitsum.cortex.ingest.service;

import ai.fitsum.cortex.api.config.CortexProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chunks text into overlapping segments optimized for embedding and retrieval.
 * Uses configurable token target and overlap; hard-wraps oversize blocks to
 * keep under the model context (BGE-Large ~512 tokens).
 */
@Service
public class ChunkingService {

    private static final double CHARS_PER_TOKEN = 3.2; // safer for BERT/BGE

    private final CortexProperties properties;

    public ChunkingService(CortexProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(String text, String documentTitle) {
        int targetTokens = properties.getIngestion().getChunkSizeTokens();
        int overlapPercent = properties.getIngestion().getChunkOverlapPercent();
        int maxChars = (int) Math.round(targetTokens * CHARS_PER_TOKEN);

        List<TextChunk> chunks = new ArrayList<>();

        // Prefer paragraph boundaries; fallback to single-line boundaries
        String[] blocks = text.split("\\R{2,}");
        if (blocks.length == 1) {
            blocks = text.split("\\R");
        }

        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;
        String currentHeading = null;

        for (String block : blocks) {
            String paragraph = block.trim();
            if (paragraph.isEmpty()) continue;

            if (isHeading(paragraph)) {
                currentHeading = paragraph;
            }

            // Flush current if appending would exceed cap
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > maxChars) {
                chunks.add(new TextChunk(chunkIndex++, current.toString().trim(), currentHeading, null));
                String overlap = extractOverlap(current.toString(), overlapPercent);
                current = new StringBuilder(overlap);
            }

            // Hard-wrap extremely large paragraphs
            if (paragraph.length() > maxChars) {
                int start = 0;
                while (start < paragraph.length()) {
                    int end = Math.min(paragraph.length(), start + maxChars);
                    String slice = paragraph.substring(start, end);
                    if (current.length() > 0) {
                        chunks.add(new TextChunk(chunkIndex++, current.toString().trim(), currentHeading, null));
                        current.setLength(0);
                    }
                    chunks.add(new TextChunk(chunkIndex++, slice.trim(), currentHeading, null));
                    start = end;
                }
            } else {
                current.append(paragraph).append("\n\n");
            }
        }

        if (current.length() > 0) {
            chunks.add(new TextChunk(chunkIndex, current.toString().trim(), currentHeading, null));
        }

        return chunks;
    }

    private boolean isHeading(String text) {
        return text.startsWith("#") ||
            (text.length() < 100 && text.endsWith(":")) ||
            text.matches("^[A-Z][A-Za-z\\s]{2,50}$");
    }

    private String extractOverlap(String text, int overlapPercent) {
        int overlapSize = Math.max(0, (int) (text.length() * overlapPercent / 100.0));
        if (overlapSize == 0 || text.length() <= overlapSize) return "";
        String tail = text.substring(text.length() - overlapSize);

        Pattern sentenceEnd = Pattern.compile("[.!?]\\s+");
        Matcher m = sentenceEnd.matcher(tail);
        if (m.find()) return tail.substring(m.end());
        return tail;
    }

    public record TextChunk(
        int index,
        String content,
        String heading,
        Integer pageNumber
    ) {}
}

