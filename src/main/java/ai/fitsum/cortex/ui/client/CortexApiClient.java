package ai.fitsum.cortex.ui.client;

import ai.fitsum.cortex.ui.dto.AskRequest;
import ai.fitsum.cortex.ui.dto.AskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Client for calling the Cortex API.
 */
@Service
public class CortexApiClient {
    
    private final WebClient webClient;
    
    public CortexApiClient(
        WebClient.Builder webClientBuilder,
        @Value("${cortex.api.base-url:http://localhost:8080}") String apiBaseUrl
    ) {
        this.webClient = webClientBuilder
            .baseUrl(apiBaseUrl)
            .build();
    }
    
    public AskResponse ask(AskRequest request) {
        return webClient.post()
            .uri("/v1/ask")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AskResponse.class)
            .block();
    }
    
    public Flux<String> askStream(AskRequest request) {
        return webClient.post()
            .uri("/v1/ask/stream")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class);
    }
}

