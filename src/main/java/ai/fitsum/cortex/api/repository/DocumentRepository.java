package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.Document;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends CrudRepository<Document, Long> {
    
    @Query("SELECT * FROM document WHERE source_id = :sourceId AND content_hash = :contentHash")
    Optional<Document> findBySourceIdAndContentHash(@Param("sourceId") Long sourceId, @Param("contentHash") String contentHash);
    
    @Query("SELECT * FROM document WHERE source_id = :sourceId AND external_id = :externalId")
    Optional<Document> findBySourceIdAndExternalId(@Param("sourceId") Long sourceId, @Param("externalId") String externalId);

    @Query("""
        SELECT * FROM document
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    List<Document> findRecent(@Param("limit") int limit);
}

