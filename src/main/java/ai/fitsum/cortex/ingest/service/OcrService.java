package ai.fitsum.cortex.ingest.service;

import ai.fitsum.cortex.api.config.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrProperties props;
    private final Tesseract tesseract;

    public OcrService(OcrProperties props) {
        this.props = props;
        this.tesseract = new Tesseract();
        this.tesseract.setLanguage(props.getLang());
        // Let Tess4J use default data path; users can set TESSDATA_PREFIX env var if needed
    }

    public boolean isEnabled() { return props.isEnabled(); }

    public String ocrImage(byte[] imageBytes) throws IOException {
        if (!props.isEnabled()) return null;
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) return null;
        img = clampSize(img, props.getMaxImageWidth(), props.getMaxImageHeight());
        try {
            String txt = tesseract.doOCR(img);
            return normalize(txt);
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed", e);
            return null;
        }
    }

    public String ocrPdf(byte[] pdfBytes) throws IOException {
        if (!props.isEnabled()) return null;
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder sb = new StringBuilder();
            int pages = Math.min(doc.getNumberOfPages(), props.getMaxPages());
            for (int i = 0; i < pages; i++) {
                BufferedImage page = renderer.renderImageWithDPI(i, 200, ImageType.GRAY);
                page = clampSize(page, props.getMaxImageWidth(), props.getMaxImageHeight());
                try {
                    String txt = tesseract.doOCR(page);
                    if (txt != null && !txt.isBlank()) {
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(txt.trim());
                    }
                } catch (TesseractException e) {
                    log.warn("Tesseract OCR failed on page {}", i, e);
                }
            }
            String out = normalize(sb.toString());
            return out.isBlank() ? null : out;
        }
    }

    private static BufferedImage clampSize(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        return s.replaceAll("\r", "").replaceAll("\s+", " ").trim();
    }
}


