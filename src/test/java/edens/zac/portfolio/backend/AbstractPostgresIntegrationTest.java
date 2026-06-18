package edens.zac.portfolio.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for DB-touching integration tests. Boots a real Postgres 16 container, seeds the
 * pre-Flyway base schema via {@code db/test-base-schema.sql} (so the V2..V28 ALTERs have tables to
 * act on), wires the container to the Spring DataSource via {@link ServiceConnection}, and lets
 * Flyway baseline the non-empty DB at 0 and apply V2..V29 on top on context start — so
 * security-critical SQL actually executes. This mirrors prod (already baselined at 0) without
 * adding any migration below the prod max applied version. Requires Docker to be running.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("db/test-base-schema.sql");
}
