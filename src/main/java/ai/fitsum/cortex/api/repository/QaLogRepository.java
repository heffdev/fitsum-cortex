package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.QaLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QaLogRepository extends CrudRepository<QaLog, Long> {
    
    List<QaLog> findByUserId(String userId);
    
    List<QaLog> findBySessionId(String sessionId);
}

