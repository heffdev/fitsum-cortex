package ai.fitsum.cortex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.web-application-type=reactive"
    }
)
@ActiveProfiles("test")
class FitsumCortexApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context loads successfully
    }

}

