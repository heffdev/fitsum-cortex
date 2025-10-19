package ai.fitsum.cortex.api.controller;

import ai.fitsum.cortex.api.dto.AskRequest;
import ai.fitsum.cortex.api.dto.AskResponse;
import ai.fitsum.cortex.api.service.RagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST API for RAG question-answering.
 */
@RestController
@RequestMapping("/v1")
public class AskController {
    
    private static final Logger log = LoggerFactory.getLogger(AskController.class);
    
    private final RagService ragService;
    
    public AskController(RagService ragService) {
        this.ragService = ragService;
    }
    
    @PostMapping("/ask")
    public AskResponse ask(
        @Valid @RequestBody AskRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        String userId = user != null ? user.getUsername() : "anonymous";
        log.info("Ask request from user: {}", userId);
        
        return ragService.ask(request, userId);
    }
    
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(
        @Valid @RequestBody AskRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        String userId = user != null ? user.getUsername() : "anonymous";
        log.info("Streaming ask request from user: {}", userId);
        
        return ragService.askStream(request, userId);
    }
}

