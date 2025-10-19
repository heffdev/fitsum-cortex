package ai.fitsum.cortex.ui.view;

import ai.fitsum.cortex.ui.client.CortexApiClient;
import ai.fitsum.cortex.ingest.service.IngestionService;
import ai.fitsum.cortex.ui.dto.AskRequest;
import ai.fitsum.cortex.ui.dto.AskResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Main Q&A interface for Fitsum Cortex.
 * Features: streaming answers, citations, privacy indicators, trace IDs.
 */
@Route("")
@AnonymousAllowed
public class MainView extends VerticalLayout {
    
    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    
    private final CortexApiClient apiClient;
    private final TextField questionField;
    private final Checkbox allowFallbackCheckbox;
    private final Button askButton;
    private final Div answerContainer;
    private final Div citationsContainer;
    private final Div metadataFooter;
    private final String sessionId;
    private final IngestionService ingestionService;
    
    public MainView(CortexApiClient apiClient, IngestionService ingestionService) {
        this.apiClient = apiClient;
        this.ingestionService = ingestionService;
        this.sessionId = UUID.randomUUID().toString();
        
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        
        // Header
        H1 header = new H1("🧠 Fitsum Cortex");
        header.getStyle().set("margin-bottom", "0");
        
        Paragraph subtitle = new Paragraph("Your private knowledge assistant");
        subtitle.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin-top", "0");
        
        // Question input
        questionField = new TextField();
        questionField.setPlaceholder("Ask a question about your knowledge base...");
        questionField.setWidthFull();
        questionField.setClearButtonVisible(true);
        
        // Controls
        allowFallbackCheckbox = new Checkbox("Allow general knowledge fallback");
        allowFallbackCheckbox.setValue(false);
        allowFallbackCheckbox.getStyle().set("font-size", "0.9em");
        
        askButton = new Button("Ask", event -> handleAsk());
        askButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        askButton.setDisableOnClick(true);
        
        HorizontalLayout controls = new HorizontalLayout(allowFallbackCheckbox, askButton);
        controls.setWidthFull();
        controls.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controls.setAlignItems(Alignment.CENTER);
        
        // Answer display
        answerContainer = new Div();
        answerContainer.addClassName("answer-container");
        answerContainer.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("padding", "1.5rem")
            .set("border-radius", "8px")
            .set("min-height", "200px")
            .set("margin-top", "1rem");
        
        // Citations display
        H3 citationsHeader = new H3("📚 Sources");
        citationsHeader.getStyle().set("margin-top", "2rem");
        
        citationsContainer = new Div();
        citationsContainer.addClassName("citations-container");
        
        // Metadata footer
        metadataFooter = new Div();
        metadataFooter.getStyle()
            .set("margin-top", "2rem")
            .set("padding-top", "1rem")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("font-size", "0.85em")
            .set("color", "var(--lumo-secondary-text-color)");
        
        // Drag & drop upload
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setDropLabel(new Span("Drop files to ingest"));
        upload.setMaxFiles(1);
        upload.setAcceptedFileTypes(".pdf", ".txt", ".md", ".docx");
        upload.addSucceededListener(e -> {
            try {
                byte[] bytes = buffer.getInputStream().readAllBytes();
                Long docId = ingestionService.ingestLocalFile(bytes, e.getFileName());
                Notification.show("Ingested: " + e.getFileName() + " (ID: " + docId + ")");
            } catch (Exception ex) {
                Notification.show("Upload failed: " + ex.getMessage());
                log.error("Upload failed", ex);
            }
        });

        // Layout
        add(
            header,
            subtitle,
            questionField,
            controls,
            answerContainer,
            citationsHeader,
            citationsContainer,
            metadataFooter,
            upload
        );
        
        // Initially hide citations and footer
        citationsHeader.setVisible(false);
        citationsContainer.setVisible(false);
        metadataFooter.setVisible(false);
        
        // Enter key support
        questionField.addKeyPressListener(event -> {
            if (event.getKey().equals("Enter")) {
                handleAsk();
            }
        });
    }
    
    private void handleAsk() {
        String question = questionField.getValue().trim();
        if (question.isEmpty()) {
            askButton.setEnabled(true);
            return;
        }
        
        // Clear previous results
        answerContainer.removeAll();
        citationsContainer.removeAll();
        metadataFooter.removeAll();
        
        // Show loading
        Paragraph loading = new Paragraph("🔍 Searching knowledge base and generating answer...");
        loading.getStyle().set("font-style", "italic");
        answerContainer.add(loading);
        
        // Make API call
        AskRequest request = new AskRequest(
            question,
            null,
            allowFallbackCheckbox.getValue(),
            sessionId
        );
        
        try {
            AskResponse response = apiClient.ask(request);
            displayAnswer(response);
        } catch (Exception e) {
            log.error("Error asking question", e);
            displayError(e.getMessage());
        } finally {
            askButton.setEnabled(true);
        }
    }
    
    private void displayAnswer(AskResponse response) {
        answerContainer.removeAll();
        
        // Privacy chip
        if (!"NONE".equals(response.sensitivity())) {
            Span privacyChip = new Span("🔒 " + response.sensitivity());
            privacyChip.getStyle()
                .set("display", "inline-block")
                .set("padding", "0.25rem 0.75rem")
                .set("background", "var(--lumo-error-color-10pct)")
                .set("color", "var(--lumo-error-color)")
                .set("border-radius", "12px")
                .set("font-size", "0.85em")
                .set("margin-bottom", "1rem");
            answerContainer.add(privacyChip);
        }
        
        // Answer text
        Div answerText = new Div();
        answerText.getElement().setProperty("innerHTML", 
            response.answer().replace("\n", "<br>"));
        answerText.getStyle()
            .set("line-height", "1.6")
            .set("font-size", "1.1em");
        answerContainer.add(answerText);
        
        // Confidence indicator
        Span confidence = new Span("Confidence: " + response.confidence());
        confidence.getStyle()
            .set("display", "block")
            .set("margin-top", "1rem")
            .set("font-size", "0.9em")
            .set("color", "var(--lumo-secondary-text-color)");
        answerContainer.add(confidence);
        
        // Display citations
        if (response.citations() != null && !response.citations().isEmpty()) {
            displayCitations(response.citations());
        }
        
        // Display metadata
        displayMetadata(response);
    }
    
    private void displayCitations(java.util.List<AskResponse.Citation> citations) {
        citationsContainer.removeAll();
        citationsContainer.setVisible(true);
        citationsContainer.getElement().getParent().setVisible(true);  // Show header too
        
        citations.forEach(citation -> {
            Div citationCard = new Div();
            citationCard.getStyle()
                .set("background", "white")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "6px")
                .set("padding", "1rem")
                .set("margin-bottom", "0.75rem");
            
            H4 title = new H4(citation.documentTitle());
            title.getStyle().set("margin", "0 0 0.5rem 0");
            
            Paragraph location = new Paragraph("📍 " + citation.location());
            location.getStyle()
                .set("margin", "0 0 0.5rem 0")
                .set("font-size", "0.9em")
                .set("color", "var(--lumo-secondary-text-color)");
            
            Paragraph snippet = new Paragraph(citation.snippet());
            snippet.getStyle()
                .set("margin", "0")
                .set("font-size", "0.9em")
                .set("font-style", "italic");
            
            citationCard.add(title, location, snippet);
            citationsContainer.add(citationCard);
        });
    }
    
    private void displayMetadata(AskResponse response) {
        metadataFooter.removeAll();
        metadataFooter.setVisible(true);
        
        String metadata = String.format(
            "Provider: %s | Latency: %dms | Trace ID: %s",
            response.provider(),
            response.latencyMs(),
            response.traceId()
        );
        
        Paragraph metadataText = new Paragraph(metadata);
        metadataText.getStyle().set("margin", "0");
        metadataFooter.add(metadataText);
    }
    
    private void displayError(String errorMessage) {
        answerContainer.removeAll();
        
        Div errorDiv = new Div();
        errorDiv.getStyle()
            .set("color", "var(--lumo-error-text-color)")
            .set("background", "var(--lumo-error-color-10pct)")
            .set("padding", "1rem")
            .set("border-radius", "6px");
        
        Paragraph errorText = new Paragraph("❌ " + errorMessage);
        errorText.getStyle().set("margin", "0");
        errorDiv.add(errorText);
        
        answerContainer.add(errorDiv);
    }
}

