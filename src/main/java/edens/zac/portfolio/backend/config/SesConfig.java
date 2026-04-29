package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Builds the {@link SesV2Client} bean used by {@link
 * edens.zac.portfolio.backend.services.EmailService} for transactional gallery emails. Mirrors
 * {@link S3Config} so SES inherits the same region and the same static AWS credentials.
 */
@Slf4j
@Configuration
public class SesConfig {

  @Value("${aws.access.key.id}")
  private String accessKeyId;

  @Value("${aws.secret.access.key}")
  private String secretAccessKey;

  @Value("${aws.s3.region}")
  private String region;

  @PostConstruct
  public void logConfig() {
    log.info("SesConfig initialized");
    log.info("Region: {}", region);
  }

  @Bean(destroyMethod = "close")
  public SesV2Client sesV2Client() {
    log.info("Creating SesV2Client (region={})", region);
    try {
      AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
      return SesV2Client.builder()
          .region(Region.of(region))
          .credentialsProvider(StaticCredentialsProvider.create(credentials))
          .build();
    } catch (Exception e) {
      log.error("Failed to create SesV2Client", e);
      throw e;
    }
  }
}
