package ai.fitsum.cortex.api.controller;

import ai.fitsum.cortex.ingest.service.FolderWatcherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/watcher")
public class WatcherController {

    private final FolderWatcherService watcherService;

    public WatcherController(FolderWatcherService watcherService) {
        this.watcherService = watcherService;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(watcherService.status());
    }

    @PostMapping("/scan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> scan() {
        watcherService.scanNow();
        return ResponseEntity.ok(watcherService.status());
    }
}


