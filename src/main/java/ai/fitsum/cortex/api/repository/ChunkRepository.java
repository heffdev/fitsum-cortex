package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.Chunk;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends CrudRepository<Chunk, Long> {
    
    @Query("""
        SELECT c.id, c.document_id, c.chunk_index, c.content, c.content_hash,
               c.token_count, c.heading, c.page_number, c.created_at
        FROM chunk c
        WHERE c.document_id = :documentId
        ORDER BY c.chunk_index
        """)
    List<Chunk> findByDocumentId(@Param("documentId") Long documentId);
    
    @Query("""
        SELECT c.id, c.document_id, c.chunk_index, c.content, c.content_hash,
               c.token_count, c.heading, c.page_number, c.created_at
        FROM chunk c
        WHERE c.id = ANY(:ids)
        """)
    List<Chunk> findByIdIn(@Param("ids") Long[] ids);
    
    @Query("""
        WITH q AS (
            SELECT websearch_to_tsquery('english', :query) AS tsq, :query AS raw
        )
        SELECT c.id, c.document_id, c.chunk_index, c.content, c.content_hash,
               c.token_count, c.heading, c.page_number, c.created_at
        FROM (
            SELECT c.id
            FROM chunk c, q
            WHERE to_tsvector('english', c.content) @@ q.tsq
               OR c.content % q.raw
            ORDER BY (
                ts_rank(to_tsvector('english', c.content), q.tsq) * 0.7
              + similarity(c.content, q.raw) * 0.3
            ) DESC
            LIMIT :limit
        ) ids
        JOIN chunk c ON c.id = ids.id
        ORDER BY c.id
        """)
    List<Chunk> fullTextSearch(@Param("query") String query, @Param("limit") int limit);
    
    @Query("""
        SELECT c.id, c.document_id, c.chunk_index, c.content, c.content_hash,
               c.token_count, c.heading, c.page_number, c.created_at
        FROM chunk c
        ORDER BY c.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """)
    List<Chunk> vectorSearch(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query("SELECT COUNT(*) FROM chunk WHERE document_id = :documentId")
    long countByDocumentId(@Param("documentId") Long documentId);
}

