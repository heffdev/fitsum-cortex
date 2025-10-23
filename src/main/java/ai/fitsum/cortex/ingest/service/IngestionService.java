package ai.fitsum.cortex.ingest.service;

import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.domain.Source;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.repository.SourceRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import ai.fitsum.cortex.api.config.CortexProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class IngestionService {

    private final DocumentNormalizer documentNormalizer;
    private final ChunkingService chunkingService;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final SourceRepository sourceRepository;
    private final CortexProperties properties;
    @Value("${spring.ai.vectorstore.pgvector.dimensions}")
    private int embeddingDimensions;

    public IngestionService(
        DocumentNormalizer documentNormalizer,
        ChunkingService chunkingService,
        @Qualifier("cortexEmbeddingModel") EmbeddingModel embeddingModel,
        DocumentRepository documentRepository,
        ChunkRepository chunkRepository,
        SourceRepository sourceRepository,
        CortexProperties properties
    ) {
        this.documentNormalizer = documentNormalizer;
        this.chunkingService = chunkingService;
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.sourceRepository = sourceRepository;
        this.properties = properties;
    }

    @Transactional
    public IngestionResult ingestLocalFile(byte[] rawBytes, String filename) throws Exception {
        // Ensure a LOCAL_FILES source exists
        Long sourceId = ensureLocalFilesSource();

        // Normalize & compute content hash
        DocumentNormalizer.NormalizedDocument normalized = documentNormalizer.normalize(rawBytes, filename);

        // Deduplicate by (source_id, content_hash)
        var existing = documentRepository.findBySourceIdAndContentHash(sourceId, normalized.contentHash());
        if (existing.isPresent()) {
            return new IngestionResult(
                existing.get().id(),
                normalized.title(),
                filename,
                rawBytes.length,
                java.time.Instant.now(),
                true
            );
        }

        // Persist document row
        Document document = Document.create(
            sourceId,
            filename,
            normalized.title(),
            normalized.contentHash(),
            normalized.contentType(),
            normalized.text(), // Store raw content
            null
        );
        document = documentRepository.save(document);

        // Chunk
        List<ChunkingService.TextChunk> textChunks = chunkingService.chunk(
            normalized.text(), normalized.title()
        );

        // Embed and persist chunks
        for (ChunkingService.TextChunk tc : textChunks) {
            // Enforce max chars per chunk prior to embedding
            int targetTokens = properties.getIngestion().getChunkSizeTokens();
            int maxChars = (int) Math.round(targetTokens * 3.2);
            String content = tc.content();
            if (content.length() > maxChars) {
                content = content.substring(0, maxChars);
            }
            float[] emb = embeddingModel.embed(content);
            if (emb == null || emb.length != embeddingDimensions) {
                throw new IllegalStateException(
                    "Embedding dimension mismatch: expected " + embeddingDimensions +
                    ", got " + (emb == null ? 0 : emb.length) +
                    ". Verify LM Studio is serving 'text-embedding-bge-large-en-v1.5' and OPENAI_BASE_URL points to it."
                );
            }
            Chunk c = Chunk.create(
                document.id(),
                tc.index(),
                content,
                sha256(tc.content().getBytes(StandardCharsets.UTF_8)),
                tc.content().length(),
                tc.heading(),
                tc.pageNumber(),
                emb
            );
            chunkRepository.save(c);
        }

        return new IngestionResult(
            document.id(),
            normalized.title(),
            filename,
            rawBytes.length,
            java.time.Instant.now(),
            false
        );
    }

    private Long ensureLocalFilesSource() {
        var list = sourceRepository.findBySourceType("LOCAL_FILES");
        if (list != null && !list.isEmpty()) {
            return list.get(0).id();
        }
        Source src = Source.create("Local Files", Source.SourceType.LOCAL_FILES, null);
        src = sourceRepository.save(src);
        return src.id();
    }

    private String sha256(byte[] content) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}


