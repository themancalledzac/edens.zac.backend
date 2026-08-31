package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Every AWS client the application uses, sharing one static credentials provider.
 *
 * <p>All four clients take the same credentials. Three take {@code aws.s3.region}; CloudFront is a
 * global service and must keep {@link Region#AWS_GLOBAL}. The property key is {@code aws.s3.region}
 * rather than a neutral {@code aws.region} because 51 test classes supply it by that name to start
 * their context.
 */
@Slf4j
@Configuration
public class AwsClientConfig {

  @Value("${aws.access.key.id}")
  private String accessKeyId;

  @Value("${aws.secret.access.key}")
  private String secretAccessKey;

  @Value("${aws.s3.region}")
  private String region;

  @PostConstruct
  public void logConfig() {
    log.info("AwsClientConfig initialized");
    log.info("Region: {}", region);
    log.info("Access key length: {}", accessKeyId != null ? accessKeyId.length() : "null");
    log.info("Secret key length: {}", secretAccessKey != null ? secretAccessKey.length() : "null");
  }

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
  }

  @Bean(destroyMethod = "close")
  public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
    log.info("Creating S3Client");
    return S3Client.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  /**
   * Presigner for generating short-lived, self-authenticating S3 GET URLs. Downloads redirect (302)
   * to these so the bytes stream straight from S3 to the client, bypassing the Amplify Web Compute
   * 5.72 MB response cap that kills anything proxied through the Next.js BFF.
   */
  @Bean(destroyMethod = "close")
  public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
    log.info("Creating S3Presigner");
    return S3Presigner.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  @Bean(destroyMethod = "close")
  public CloudFrontClient cloudFrontClient(AwsCredentialsProvider credentialsProvider) {
    log.info("Creating CloudFrontClient");
    return CloudFrontClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.AWS_GLOBAL)
        .build();
  }

  /** Used by {@link edens.zac.portfolio.backend.services.EmailService} for transactional email. */
  @Bean(destroyMethod = "close")
  public SesV2Client sesV2Client(AwsCredentialsProvider credentialsProvider) {
    log.info("Creating SesV2Client (region={})", region);
    return SesV2Client.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .build();
  }
}
