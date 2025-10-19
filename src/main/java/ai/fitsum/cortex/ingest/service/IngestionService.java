package ai.fitsum.cortex.ingest.service;

import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.domain.Source;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.repository.SourceRepository;
import org.springframework.ai.embedding.EmbeddingModel;
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

    public IngestionService(
        DocumentNormalizer documentNormalizer,
        ChunkingService chunkingService,
        EmbeddingModel embeddingModel,
        DocumentRepository documentRepository,
        ChunkRepository chunkRepository,
        SourceRepository sourceRepository
    ) {
        this.documentNormalizer = documentNormalizer;
        this.chunkingService = chunkingService;
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public Long ingestLocalFile(byte[] rawBytes, String filename) throws Exception {
        // Ensure a LOCAL_FILES source exists
        Long sourceId = ensureLocalFilesSource();

        // Normalize & compute content hash
        DocumentNormalizer.NormalizedDocument normalized = documentNormalizer.normalize(rawBytes, filename);

        // Deduplicate by (source_id, content_hash)
        var existing = documentRepository.findBySourceIdAndContentHash(sourceId, normalized.contentHash());
        if (existing.isPresent()) {
            return existing.get().id();
        }

        // Persist document row
        Document document = Document.create(
            sourceId,
            filename,
            normalized.title(),
            normalized.contentHash(),
            normalized.contentType(),
            null,
            null
        );
        document = documentRepository.save(document);

        // Chunk
        List<ChunkingService.TextChunk> textChunks = chunkingService.chunk(
            normalized.text(), normalized.title()
        );

        // Embed and persist chunks
        for (ChunkingService.TextChunk tc : textChunks) {
            float[] emb = embeddingModel.embed(tc.content());
            Chunk c = Chunk.create(
                document.id(),
                tc.index(),
                tc.content(),
                sha256(tc.content().getBytes(StandardCharsets.UTF_8)),
                tc.content().length(),
                tc.heading(),
                tc.pageNumber(),
                emb
            );
            chunkRepository.save(c);
        }

        return document.id();
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


