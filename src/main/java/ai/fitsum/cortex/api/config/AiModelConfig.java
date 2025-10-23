package ai.fitsum.cortex.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

/**
 * Force the application to use the OpenAI-compatible embedding model (LM Studio)
 * as the primary {@link EmbeddingModel}. This avoids accidentally wiring a
 * local Transformers-based embedding model (often 384 dims) when both are on the classpath.
 */
@Configuration
public class AiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);

    @Bean
    @Primary
    public EmbeddingModel cortexEmbeddingModel(OpenAiEmbeddingModel openAiEmbeddingModel) {
        log.info("Using OpenAI-compatible EmbeddingModel (LM Studio) as primary embedding provider");
        return openAiEmbeddingModel;
    }
}


