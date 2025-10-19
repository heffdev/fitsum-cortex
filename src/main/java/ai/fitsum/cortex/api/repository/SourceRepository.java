package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.Source;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SourceRepository extends CrudRepository<Source, Long> {
    
    List<Source> findByIsEnabled(Boolean isEnabled);
    
    @Query("SELECT * FROM source WHERE source_type = :sourceType")
    List<Source> findBySourceType(String sourceType);
}

