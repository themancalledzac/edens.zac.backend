package edens.zac.portfolio.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "aws.s3.region=us-west-2",
    "aws.s3.bucket.name=test-bucket", 
    "aws.s3.access.key=test-key",
    "aws.s3.secret.key=test-secret",
    "aws.cloudfront.domain=test.com"
})
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}