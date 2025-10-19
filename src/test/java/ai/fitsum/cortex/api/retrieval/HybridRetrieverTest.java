package ai.fitsum.cortex.api.retrieval;

import ai.fitsum.cortex.api.config.CortexProperties;
import ai.fitsum.cortex.api.domain.Chunk;
import ai.fitsum.cortex.api.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {
    
    @Mock
    private ChunkRepository chunkRepository;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @Mock
    private ReRanker reRanker;
    
    private CortexProperties properties;
    private HybridRetriever retriever;
    
    @BeforeEach
    void setUp() {
        properties = new CortexProperties();
        retriever = new HybridRetriever(chunkRepository, embeddingModel, reRanker, properties);
    }
    
    @Test
    void retrieve_combinesFtsAndAnnResults() {
        // Given
        String query = "test query";
        
        Chunk chunk1 = createTestChunk(1L, "FTS result");
        Chunk chunk2 = createTestChunk(2L, "ANN result");
        
        when(chunkRepository.fullTextSearch(eq(query), anyInt()))
            .thenReturn(List.of(chunk1));
        
        when(chunkRepository.vectorSearch(anyString(), anyInt()))
            .thenReturn(List.of(chunk2));
        
        when(embeddingModel.embed(query))
            .thenReturn(new float[1536]);
        
        when(reRanker.rerank(eq(query), anyList()))
            .thenReturn(List.of(
                RetrievedChunk.of(chunk1, 0.9, "reranked"),
                RetrievedChunk.of(chunk2, 0.8, "reranked")
            ));
        
        // When
        List<RetrievedChunk> results = retriever.retrieve(query, 10);
        
        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().id()).isEqualTo(1L);
        assertThat(results.get(1).chunk().id()).isEqualTo(2L);
    }
    
    @Test
    void retrieve_respectsMaxChunksLimit() {
        // Given
        String query = "test query";
        properties.getRetrieval().setMaxChunks(2);
        
        List<Chunk> manyChunks = List.of(
            createTestChunk(1L, "chunk1"),
            createTestChunk(2L, "chunk2"),
            createTestChunk(3L, "chunk3"),
            createTestChunk(4L, "chunk4")
        );
        
        when(chunkRepository.fullTextSearch(anyString(), anyInt()))
            .thenReturn(manyChunks.subList(0, 2));
        
        when(chunkRepository.vectorSearch(anyString(), anyInt()))
            .thenReturn(manyChunks.subList(2, 4));
        
        when(embeddingModel.embed(query))
            .thenReturn(new float[1536]);
        
        when(reRanker.rerank(eq(query), anyList()))
            .thenReturn(manyChunks.stream()
                .map(c -> RetrievedChunk.of(c, 0.5, "reranked"))
                .toList());
        
        // When
        List<RetrievedChunk> results = retriever.retrieve(query, 10);
        
        // Then
        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }
    
    private Chunk createTestChunk(Long id, String content) {
        return new Chunk(
            id,
            1L,
            0,
            content,
            "hash" + id,
            100,
            null,
            null,
            new float[1536],
            LocalDateTime.now()
        );
    }
}

