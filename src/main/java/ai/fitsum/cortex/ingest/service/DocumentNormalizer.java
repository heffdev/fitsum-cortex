package ai.fitsum.cortex.ingest.service;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Normalizes documents from various formats to plain text.
 * Uses Apache Tika for format detection and parsing.
 */
@Service
public class DocumentNormalizer {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentNormalizer.class);
    
    private final AutoDetectParser parser = new AutoDetectParser();
    
    public NormalizedDocument normalize(byte[] rawContent, String fileName) throws IOException {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);  // no limit
            Metadata metadata = new Metadata();
            metadata.set("resourceName", fileName);
            
            ParseContext parseContext = new ParseContext();
            parser.parse(
                new ByteArrayInputStream(rawContent),
                handler,
                metadata,
                parseContext
            );
            
            String text = handler.toString();
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            String title = extractTitle(metadata, fileName);
            String contentHash = computeSha256(rawContent);
            
            log.debug("Normalized document: {} (type: {}, {} chars)", title, contentType, text.length());
            
            return new NormalizedDocument(
                title,
                text,
                contentType,
                contentHash,
                metadata
            );
            
        } catch (Exception e) {
            throw new IOException("Failed to parse document: " + fileName, e);
        }
    }
    
    public NormalizedDocument normalizeText(String text, String title) {
        String contentHash = computeSha256(text.getBytes(StandardCharsets.UTF_8));
        return new NormalizedDocument(
            title,
            text,
            "text/plain",
            contentHash,
            new Metadata()
        );
    }
    
    private String extractTitle(Metadata metadata, String fallback) {
        String title = metadata.get("title");
        if (title != null && !title.isBlank()) {
            return title;
        }
        
        // Fallback: use filename without extension
        String name = fallback.replaceFirst("[.][^.]+$", "");
        return name;
    }
    
    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    public record NormalizedDocument(
        String title,
        String text,
        String contentType,
        String contentHash,
        Metadata metadata
    ) {}
}

