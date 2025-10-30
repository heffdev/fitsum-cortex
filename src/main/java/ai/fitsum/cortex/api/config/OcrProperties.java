package ai.fitsum.cortex.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cortex.ocr")
public class OcrProperties {
    private boolean enabled = false;
    private String lang = "eng";
    private int maxPages = 10;
    private int maxImageWidth = 3000;
    private int maxImageHeight = 3000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
    public int getMaxImageWidth() { return maxImageWidth; }
    public void setMaxImageWidth(int maxImageWidth) { this.maxImageWidth = maxImageWidth; }
    public int getMaxImageHeight() { return maxImageHeight; }
    public void setMaxImageHeight(int maxImageHeight) { this.maxImageHeight = maxImageHeight; }
}


