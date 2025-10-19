package ai.fitsum.cortex.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides RestClient.Builder bean for Spring AI OpenAI integration.
 * Needed because we're using reactive web but OpenAI client needs RestClient.
 */
@Configuration
public class RestClientConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

