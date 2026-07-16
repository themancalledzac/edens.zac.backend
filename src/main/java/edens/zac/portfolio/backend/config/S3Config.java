package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

  @Bean(destroyMethod = "close")
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

  /**
   * Presigner for generating short-lived, self-authenticating S3 GET URLs. Downloads redirect (302)
   * to these so the bytes stream straight from S3 to the client, bypassing the Amplify Web Compute
   * 5.72 MB response cap that kills anything proxied through the Next.js BFF.
   */
  @Bean(destroyMethod = "close")
  public S3Presigner s3Presigner() {
    log.info("Creating S3Presigner");
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    return S3Presigner.builder()
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .region(Region.of(region))
        .build();
  }

  @Bean(destroyMethod = "close")
  public CloudFrontClient cloudFrontClient() {
    log.info("Creating CloudFrontClient");
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    return CloudFrontClient.builder()
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .region(Region.AWS_GLOBAL)
        .build();
  }
}
