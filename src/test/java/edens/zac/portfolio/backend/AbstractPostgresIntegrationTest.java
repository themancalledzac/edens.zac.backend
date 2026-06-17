package edens.zac.portfolio.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for DB-touching integration tests. Boots a real Postgres 16 container, wires it to the
 * Spring DataSource via {@link ServiceConnection}, and lets Flyway build the full V1..V29 schema on
 * context start — so security-critical SQL actually executes. Requires Docker to be running.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
