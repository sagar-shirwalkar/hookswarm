package dev.hookswarm.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    // Removed @Testcontainers and @Container annotations
    // We manage lifecycle manually to ensure it persists across test classes
    static final PostgreSQLContainer<?> postgres;

    static {
        // Force Docker config (keep this from before)
        System.setProperty("DOCKER_API_VERSION", "1.44");

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("hookswarm_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Helper for local dev speed

        postgres.start(); // Start explicitly
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);

        registry.add("hookswarm.outbox.poll-interval-ms", () -> "999999999");
        registry.add("hookswarm.delivery.poll-interval-ms", () -> "999999999");
        registry.add("hookswarm.recovery.interval-ms", () -> "999999999");
    }

}