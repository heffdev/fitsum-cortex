package ai.fitsum.cortex.ingest.service;

import ai.fitsum.cortex.api.config.WatcherProperties;
import ai.fitsum.cortex.api.repository.WatcherStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FolderWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FolderWatcherService.class);

    private final WatcherProperties props;
    private final IngestionService ingestionService;
    private final WatcherStateRepository stateRepository;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc;

    public FolderWatcherService(WatcherProperties props, IngestionService ingestionService, WatcherStateRepository stateRepository,
                                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc) {
        this.props = props;
        this.ingestionService = ingestionService;
        this.stateRepository = stateRepository;
        this.namedJdbc = namedJdbc;
    }

    @Scheduled(fixedDelayString = "${cortex.watcher.poll-interval:PT30S}")
    public void poll() {
        if (!props.isEnabled()) return;
        if (props.getRoot() == null || props.getRoot().isBlank()) return;
        Path root = Paths.get(props.getRoot());
        if (!Files.isDirectory(root)) return;
        try {
            int maxDepth = props.isRecursive() ? Integer.MAX_VALUE : 1;
            Set<String> ext = new HashSet<>(props.getExtensions());
            List<PathMatcher> ignores = new ArrayList<>();
            for (String g : props.getIgnoreGlobs()) {
                ignores.add(root.getFileSystem().getPathMatcher("glob:" + g));
            }
            try (var stream = Files.walk(root, maxDepth)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(root, p, ignores))
                    .filter(p -> hasAllowedExtension(p, ext))
                    .forEach(p -> ingestIfChanged(root, p));
            }
        } catch (IOException e) {
            log.warn("Folder watcher scan error", e);
        }
    }

    private boolean isIgnored(Path root, Path p, List<PathMatcher> ignores) {
        Path rel = root.relativize(p);
        for (PathMatcher m : ignores) {
            if (m.matches(rel)) return true;
        }
        return false;
    }

    private boolean hasAllowedExtension(Path p, Set<String> allowed) {
        String name = p.getFileName().toString().toLowerCase();
        int i = name.lastIndexOf('.') ;
        if (i < 0) return false;
        return allowed.contains(name.substring(i+1));
    }

    private void ingestIfChanged(Path root, Path file) {
        try {
            long lm = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            String key = file.toAbsolutePath().toString();
            var existing = stateRepository.findById(key);
            if (existing.isPresent()) {
                var st = existing.get();
                if (st.lastModified() != null && st.size() != null && st.lastModified() == lm && st.size() == size) {
                    return; // unchanged
                }
            }
            byte[] bytes = Files.readAllBytes(file);
            var result = ingestionService.ingestLocalFile(bytes, file.getFileName().toString());
            String hash = sha256(bytes);
            upsertState(key, lm, size, hash);
            log.info("Ingested file via watcher: {} -> doc {}", file, result.documentId());

            // Move processed file if configured
            if (props.getProcessedRoot() != null && !props.getProcessedRoot().isBlank()) {
                moveToProcessed(root, file);
            }
        } catch (Exception e) {
            log.warn("Failed to ingest file {}", file, e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void upsertState(String path, long lastModified, long size, String hash) {
        var sql = "INSERT INTO watcher_state(path,last_modified,size,content_hash) VALUES(:p,:lm,:s,:h) " +
                "ON CONFLICT (path) DO UPDATE SET last_modified=:lm, size=:s, content_hash=:h";
        var params = new java.util.HashMap<String, Object>();
        params.put("p", path);
        params.put("lm", lastModified);
        params.put("s", size);
        params.put("h", hash);
        namedJdbc.update(sql, params);
    }

    private void moveToProcessed(Path root, Path file) {
        try {
            Path processedRoot = Paths.get(props.getProcessedRoot());
            Path rel = root.relativize(file);
            Path target = processedRoot.resolve(rel);
            Files.createDirectories(target.getParent());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved processed file {} -> {}", file, target);
        } catch (Exception ex) {
            log.warn("Failed to move processed file {}", file, ex);
        }
    }
}


