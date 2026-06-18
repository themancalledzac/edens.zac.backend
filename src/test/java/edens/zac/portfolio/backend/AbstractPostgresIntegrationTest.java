package edens.zac.portfolio.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for DB-touching integration tests. Boots a real Postgres 16 container once per JVM run
 * (singleton pattern — Ryuk reaps it at JVM exit), seeds the pre-Flyway base schema via {@code
 * db/test-base-schema.sql}, and wires the container URL/credentials into the Spring DataSource via
 * {@link DynamicPropertySource}. Flyway then baselines the non-empty DB at 0 and applies V2..V29 on
 * top — exactly mirroring prod. All subclasses share the same container so the Spring TestContext
 * cache never points at a stopped container. Requires Docker to be running.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("db/test-base-schema.sql");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
