package ai.fitsum.cortex.api.controller;

import ai.fitsum.cortex.ingest.service.IngestionService;
import ai.fitsum.cortex.api.repository.DocumentRepository;
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

    public IngestController(IngestionService ingestionService, DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
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

    @DeleteMapping("/document/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        try {
            documentRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Delete failed for document {}", id, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}


