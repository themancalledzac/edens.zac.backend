package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
public class S3Config {

  @Value("${aws.access.key.id}")
  private String accessKeyId;

  @Value("${aws.secret.access.key}")
  private String secretAccessKey;

  @Value("${aws.s3.region}")
  private String region;

  @PostConstruct
  public void logConfig() {
    log.info("S3Config initialized");
    log.info("Region: {}", region);
    log.info("Access key length: {}", accessKeyId != null ? accessKeyId.length() : "null");
    log.info("Secret key length: {}", secretAccessKey != null ? secretAccessKey.length() : "null");
  }

  @Bean
  public S3Client s3Client() {
    log.info("Creating S3Client");
    try {
      AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

      return S3Client.builder()
          .credentialsProvider(StaticCredentialsProvider.create(credentials))
          .region(Region.of(region))
          .build();
    } catch (Exception e) {
      log.error("Failed to create S3Client", e);
      throw e;
    }
  }
}
