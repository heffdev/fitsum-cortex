package ai.fitsum.cortex.api.repository;

import ai.fitsum.cortex.api.domain.WatcherState;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WatcherStateRepository extends CrudRepository<WatcherState, String> {
}


