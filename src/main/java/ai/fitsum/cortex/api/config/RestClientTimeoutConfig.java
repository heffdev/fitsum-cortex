package ai.fitsum.cortex.api.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RestClientTimeoutConfig {

    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(300).toMillis());
            builder.requestFactory(factory);
        };
    }

    @Bean
    @Primary
    public RestClient.Builder primaryRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(300).toMillis());
        return RestClient.builder().requestFactory(factory);
    }
}


