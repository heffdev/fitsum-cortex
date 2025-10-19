package ai.fitsum.cortex.ingest.connector;

import ai.fitsum.cortex.ingest.service.ChunkingService;
import ai.fitsum.cortex.ingest.service.DocumentNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Connector for ingesting local files.
 * Recursively scans a directory and processes all supported file types.
 */
@Service
public class LocalFilesConnector {
    
    private static final Logger log = LoggerFactory.getLogger(LocalFilesConnector.class);
    
    private final DocumentNormalizer normalizer;
    private final ChunkingService chunker;
    
    public LocalFilesConnector(DocumentNormalizer normalizer, ChunkingService chunker) {
        this.normalizer = normalizer;
        this.chunker = chunker;
    }
    
    public List<IngestedDocument> sync(JsonNode config) throws IOException {
        String basePath = config.get("path").asText();
        boolean recursive = config.has("recursive") ? config.get("recursive").asBoolean() : true;
        
        log.info("Syncing local files from: {} (recursive: {})", basePath, recursive);
        
        Path rootPath = Paths.get(basePath);
        List<IngestedDocument> documents = new ArrayList<>();
        
        try (Stream<Path> paths = recursive ? 
                Files.walk(rootPath) : 
                Files.list(rootPath)) {
            
            paths.filter(Files::isRegularFile)
                .filter(this::isSupportedFile)
                .forEach(path -> {
                    try {
                        IngestedDocument doc = ingestFile(path);
                        documents.add(doc);
                        log.debug("Ingested: {}", path.getFileName());
                    } catch (IOException e) {
                        log.error("Failed to ingest file: {}", path, e);
                    }
                });
        }
        
        log.info("Ingested {} documents from local files", documents.size());
        return documents;
    }
    
    private IngestedDocument ingestFile(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        String fileName = path.getFileName().toString();
        
        DocumentNormalizer.NormalizedDocument normalized = normalizer.normalize(content, fileName);
        List<ChunkingService.TextChunk> chunks = chunker.chunk(normalized.text(), normalized.title());
        
        return new IngestedDocument(
            path.toString(),
            normalized.title(),
            normalized.text(),
            normalized.contentHash(),
            normalized.contentType(),
            chunks
        );
    }
    
    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") ||
               name.endsWith(".md") ||
               name.endsWith(".pdf") ||
               name.endsWith(".doc") ||
               name.endsWith(".docx") ||
               name.endsWith(".html") ||
               name.endsWith(".htm");
    }
    
    public record IngestedDocument(
        String externalId,
        String title,
        String content,
        String contentHash,
        String contentType,
        List<ChunkingService.TextChunk> chunks
    ) {}
}

