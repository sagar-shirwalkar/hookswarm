package dev.hookswarm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.delivery.batch.ReactiveBatchWriter;
import dev.hookswarm.event.consumer.ReactiveEventFanoutConsumer;

@DataR2dbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BaseRepositoryTest.TestConfig.class)  // ✅ Import test configuration
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.r2dbc.pool.enabled=false",
        // ✅ Disable background workers
        "hookswarm.fanout.enabled=false",
        "hookswarm.delivery.enabled=false",
        "hookswarm.batch-writer.enabled=false"
})
public abstract class BaseRepositoryTest {

    @Container
    protected static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // ✅ Mock beans that aren't needed for repository tests
    @MockitoBean
    protected ReactiveQueueService queueService;

    @MockitoBean(name = "reactiveBatchWriter")
    protected ReactiveBatchWriter batchWriter;

    @MockitoBean(name = "reactiveEventFanoutConsumer")
    protected ReactiveEventFanoutConsumer fanoutConsumer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @BeforeAll
    static void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.migrate();
    }

    // ✅ Test configuration to provide required beans
    @Configuration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
