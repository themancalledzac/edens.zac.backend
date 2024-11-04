package edens.zac.portfolio.backend.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.access.key.id}")
    private String accessKeyId;

    @Value("${aws.secret.access.key}")
    private String secretAccessKey;

    @Value("${aws.s3.region}")
    private String region;

    @PostConstruct  // Add this method
    public void logConfig() {
        log.info("S3Config initialized");
        log.info("Region: {}", region);
        log.info("Access key length: {}", accessKeyId != null ? accessKeyId.length() : "null");
        log.info("Secret key length: {}", secretAccessKey != null ? secretAccessKey.length() : "null");
    }

    @Bean
    public AmazonS3 amazonS3Client() {
        log.info("Creating AmazonS3 client");
        try {

            AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);

            return AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(region)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create AmazonS3 client", e);
            throw e;
        }
    }
}
