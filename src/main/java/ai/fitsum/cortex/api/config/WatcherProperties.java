package ai.fitsum.cortex.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "cortex.watcher")
public class WatcherProperties {

    private boolean enabled = false;
    private String root = "";
    private boolean recursive = true;
    private Duration pollInterval = Duration.ofSeconds(30);
    private List<String> ignoreGlobs = List.of("**/*.tmp", "**/~$*");
    private List<String> extensions = List.of("pdf","docx","md","txt","png","jpg","jpeg","webp");
    private String processedRoot = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    public boolean isRecursive() { return recursive; }
    public void setRecursive(boolean recursive) { this.recursive = recursive; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public List<String> getIgnoreGlobs() { return ignoreGlobs; }
    public void setIgnoreGlobs(List<String> ignoreGlobs) { this.ignoreGlobs = ignoreGlobs; }
    public List<String> getExtensions() { return extensions; }
    public void setExtensions(List<String> extensions) { this.extensions = extensions; }
    public String getProcessedRoot() { return processedRoot; }
    public void setProcessedRoot(String processedRoot) { this.processedRoot = processedRoot; }
}


