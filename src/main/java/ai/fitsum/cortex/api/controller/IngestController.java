package ai.fitsum.cortex.api.controller;

import ai.fitsum.cortex.ingest.service.IngestionService;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/ingest")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;

    public IngestController(IngestionService ingestionService, DocumentRepository documentRepository, ChunkRepository chunkRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            var result = ingestionService.ingestLocalFile(bytes, file.getOriginalFilename());
            return ResponseEntity.ok(result.documentId());
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        var docs = documentRepository.findRecent(Math.max(1, Math.min(limit, 50)));
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/document/{id}")
    public ResponseEntity<?> getDocument(@PathVariable("id") Long id) {
        try {
            var docOpt = documentRepository.findById(id);
            if (docOpt.isEmpty()) {
                log.info("Document {} not found", id);
                return ResponseEntity.notFound().build();
            }
            var doc = docOpt.get();
            var chunks = chunkRepository.findByDocumentId(id);
            log.info("Retrieved document id={} title='{}' chunks={}", id, doc.title(), chunks.size());
            return ResponseEntity.ok(new DocumentWithChunks(doc, chunks));
        } catch (Exception e) {
            log.error("Failed to retrieve document {}", id, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @DeleteMapping("/document/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        try {
            var docOpt = documentRepository.findById(id);
            if (docOpt.isEmpty()) {
                log.info("Delete requested for non-existent document {}", id);
                return ResponseEntity.noContent().build();
            }
            var doc = docOpt.get();
            long chunkCount = chunkRepository.countByDocumentId(id);
            documentRepository.deleteById(id); // cascades chunks via FK
            log.info("Deleted document id={} title='{}' chunks_deleted={} content_type={}", id, doc.title(), chunkCount, doc.contentType());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Delete failed for document {}", id, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    public record DocumentWithChunks(Document document, java.util.List<Chunk> chunks) {}
}


