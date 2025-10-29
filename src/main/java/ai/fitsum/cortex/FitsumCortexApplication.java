package ai.fitsum.cortex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for Fitsum Cortex.
 * Unified application combining API, Ingest, and UI modules.
 */
@SpringBootApplication
@EnableJdbcRepositories(basePackages = "ai.fitsum.cortex.api.repository")
@EnableScheduling
public class FitsumCortexApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitsumCortexApplication.class, args);
    }

}

