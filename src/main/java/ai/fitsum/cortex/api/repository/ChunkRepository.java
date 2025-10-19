package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.Chunk;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends CrudRepository<Chunk, Long> {
    
    List<Chunk> findByDocumentId(Long documentId);
    
    @Query("SELECT * FROM chunk WHERE id = ANY(:ids)")
    List<Chunk> findByIdIn(@Param("ids") Long[] ids);
    
    @Query("""
        SELECT c.*, ts_rank(to_tsvector('english', c.content), plainto_tsquery('english', :query)) as rank
        FROM chunk c
        WHERE to_tsvector('english', c.content) @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
        LIMIT :limit
        """)
    List<Chunk> fullTextSearch(@Param("query") String query, @Param("limit") int limit);
    
    @Query("""
        SELECT c.* FROM chunk c
        ORDER BY c.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """)
    List<Chunk> vectorSearch(@Param("embedding") String embedding, @Param("limit") int limit);
}

