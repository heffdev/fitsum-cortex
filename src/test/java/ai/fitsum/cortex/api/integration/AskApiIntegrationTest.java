package ai.fitsum.cortex.api.integration;

import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.domain.Document;
import ai.fitsum.cortex.api.domain.Source;
import ai.fitsum.cortex.api.dto.AskRequest;
import ai.fitsum.cortex.api.dto.AskResponse;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import ai.fitsum.cortex.api.repository.DocumentRepository;
import ai.fitsum.cortex.api.repository.SourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Ask API endpoint.
 * Uses Testcontainers for Postgres.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.web-application-type=reactive"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AskApiIntegrationTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Autowired
    private SourceRepository sourceRepository;
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // Clean up
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        sourceRepository.deleteAll();
        
        // Create test data
        Source source = Source.create("test-source", Source.SourceType.LOCAL_FILES, null);
        source = sourceRepository.save(source);
        
        Document doc = Document.create(
            source.id(),
            "test-doc-1",
            "Test Document",
            "hash123",
            "text/plain",
            "This is test content about Fitsum Cortex features.",
            null
        );
        doc = documentRepository.save(doc);
        
        // Create chunk with dummy embedding
        float[] embedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            embedding[i] = (float) (Math.random() - 0.5);
        }
        
        Chunk chunk = Chunk.create(
            doc.id(),
            0,
            "Fitsum Cortex is a private knowledge hub with RAG capabilities, strict citations, and privacy-first routing.",
            "chunk_hash_1",
            25,
            "Introduction",
            1,
            embedding
        );
        chunkRepository.save(chunk);
    }
    
    @Test
    @WithMockUser(username = "test@example.com")
    void ask_withContext_returnsAnswer() throws Exception {
        // Given
        AskRequest request = new AskRequest(
            "What is Fitsum Cortex?",
            null,
            false,
            "test-session"
        );
        
        // When/Then
        webTestClient.post()
            .uri("/v1/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AskResponse.class)
            .value(response -> {
                assertThat(response).isNotNull();
                assertThat(response.answer()).isNotBlank();
                assertThat(response.traceId()).isNotBlank();
                assertThat(response.latencyMs()).isGreaterThan(0);
            });
    }
    
    @Test
    @WithMockUser(username = "test@example.com")
    void ask_withoutContext_andNoFallback_returnsError() throws Exception {
        // Given
        AskRequest request = new AskRequest(
            "What is the capital of France?",  // Not in knowledge base
            null,
            false,  // No fallback
            "test-session"
        );
        
        // When/Then
        webTestClient.post()
            .uri("/v1/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError();  // Should throw exception
    }
    
    @Test
    void ask_withoutAuthentication_returns401() throws Exception {
        // Given
        AskRequest request = new AskRequest(
            "Test question",
            null,
            false,
            "test-session"
        );
        
        // When/Then
        webTestClient.post()
            .uri("/v1/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}

