package ai.fitsum.cortex.ingest.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Utility to fetch a URL and extract readable main content using Jsoup heuristics.
 */
public class UrlFetcher {

    public static IngestionService.ExtractedPage fetchReadable(String url) throws Exception {
        Document doc = Jsoup.connect(url)
            .userAgent("FitsumCortexBot/1.0")
            .timeout(10000)
            .get();

        String title = doc.title();

        // Heuristic: prefer <article>, else the largest text block of <main>, else body text
        String text = null;
        Element article = doc.selectFirst("article");
        if (article != null) {
            text = article.text();
        }
        if (text == null || text.isBlank()) {
            Element main = doc.selectFirst("main");
            if (main != null) text = main.text();
        }
        if (text == null || text.isBlank()) {
            text = doc.body() != null ? doc.body().text() : doc.text();
        }
        if (text != null) {
            text = text.replaceAll("\\s+", " ").trim();
        }
        return new IngestionService.ExtractedPage(title, text);
    }
}


