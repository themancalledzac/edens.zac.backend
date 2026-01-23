package edens.zac.portfolio.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Disabled(
    "Context loading requires H2 schema initialization - JPA entities removed during PostgreSQL migration")
@SpringBootTest
@TestPropertySource(
    properties = {
      "aws.s3.region=us-west-2",
      "aws.portfolio.s3.bucket=test-bucket",
      "aws.access.key.id=test-key",
      "aws.secret.access.key=test-secret",
      "cloudfront.domain=test.com",
      "spring.profiles.active=test",
      "spring.datasource.url=jdbc:h2:mem:testdb",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    })
class ApplicationTests {

  @Test
  void contextLoads() {}
}
