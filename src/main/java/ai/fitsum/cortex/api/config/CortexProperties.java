package ai.fitsum.cortex.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cortex")
public class CortexProperties {
    
    private Retrieval retrieval = new Retrieval();
    private Sensitivity sensitivity = new Sensitivity();
    private Ingestion ingestion = new Ingestion();
    private Security security = new Security();
    private Observability observability = new Observability();
    
    public static class Retrieval {
        private int maxChunks = 12;
        private int ftsTopK = 25;
        private int annTopK = 25;
        private int rerankTopK = 15;
        private double similarityThreshold = 0.7;
        private boolean allowEmptyContext = false;
        
        // Getters and setters
        public int getMaxChunks() { return maxChunks; }
        public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
        
        public int getFtsTopK() { return ftsTopK; }
        public void setFtsTopK(int ftsTopK) { this.ftsTopK = ftsTopK; }
        
        public int getAnnTopK() { return annTopK; }
        public void setAnnTopK(int annTopK) { this.annTopK = annTopK; }
        
        public int getRerankTopK() { return rerankTopK; }
        public void setRerankTopK(int rerankTopK) { this.rerankTopK = rerankTopK; }
        
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
        
        public boolean isAllowEmptyContext() { return allowEmptyContext; }
        public void setAllowEmptyContext(boolean allowEmptyContext) { this.allowEmptyContext = allowEmptyContext; }
    }
    
    public static class Sensitivity {
        private Map<String, List<String>> patterns;
        
        public Map<String, List<String>> getPatterns() { return patterns; }
        public void setPatterns(Map<String, List<String>> patterns) { this.patterns = patterns; }
    }
    
    public static class Ingestion {
        private int chunkSizeTokens = 400;
        private int chunkOverlapPercent = 15;
        private int batchSize = 50;
        
        public int getChunkSizeTokens() { return chunkSizeTokens; }
        public void setChunkSizeTokens(int chunkSizeTokens) { this.chunkSizeTokens = chunkSizeTokens; }
        
        public int getChunkOverlapPercent() { return chunkOverlapPercent; }
        public void setChunkOverlapPercent(int chunkOverlapPercent) { this.chunkOverlapPercent = chunkOverlapPercent; }
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
    
    public static class Security {
        private boolean enabled = true;
        private String defaultUser = "demo@fitsum.ai";
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getDefaultUser() { return defaultUser; }
        public void setDefaultUser(String defaultUser) { this.defaultUser = defaultUser; }
    }
    
    public static class Observability {
        private boolean enabled = true;
        private String exportEndpoint = "http://localhost:4318";
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getExportEndpoint() { return exportEndpoint; }
        public void setExportEndpoint(String exportEndpoint) { this.exportEndpoint = exportEndpoint; }
    }
    
    // Main getters and setters
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    
    public Sensitivity getSensitivity() { return sensitivity; }
    public void setSensitivity(Sensitivity sensitivity) { this.sensitivity = sensitivity; }
    
    public Ingestion getIngestion() { return ingestion; }
    public void setIngestion(Ingestion ingestion) { this.ingestion = ingestion; }
    
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    
    public Observability getObservability() { return observability; }
    public void setObservability(Observability observability) { this.observability = observability; }
}

