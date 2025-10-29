package ai.fitsum.cortex.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("watcher_state")
public record WatcherState(
    @Id String path,
    Long lastModified,
    Long size,
    String contentHash
) {}


