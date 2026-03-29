package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles client gallery authentication: password validation, HMAC access token generation and
 * verification.
 */
@Service
@Slf4j
public class ClientGalleryAuthService {

  private final CollectionRepository collectionRepository;
  private final String accessTokenSecret;

  ClientGalleryAuthService(
      CollectionRepository collectionRepository,
      @Value("${app.access-token.secret}") String accessTokenSecret) {
    this.collectionRepository = collectionRepository;
    this.accessTokenSecret = accessTokenSecret;
  }

  /**
   * Validate password-based access to a client gallery.
   *
   * @param slug Collection slug
   * @param password Submitted password
   * @return true if access is granted
   */
  @Transactional(readOnly = true)
  public boolean validateClientGalleryAccess(String slug, String password) {
    log.debug("Validating access to client gallery: {}", slug);

    CollectionEntity collection =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Not password-protected -- allow access
    if (collection.getPasswordHash() == null) {
      return true;
    }

    // Password required but not provided
    if (password == null || password.isEmpty()) {
      return false;
    }

    return CollectionProcessingUtil.passwordMatches(password, collection.getPasswordHash());
  }

  /**
   * Generate a time-limited HMAC access token for a client gallery.
   *
   * @param slug Collection slug
   * @return HMAC token with embedded expiry
   */
  public String generateAccessToken(String slug) {
    long expiry = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
    String payload = slug + "|" + expiry;
    String hmac = computeHmac(payload, accessTokenSecret);
    return hmac + "|" + expiry;
  }

  /**
   * Validate a time-limited HMAC access token for a client gallery.
   *
   * @param slug Collection slug
   * @param accessToken The token to validate
   * @return true if valid and not expired
   */
  @Transactional(readOnly = true)
  public boolean validateAccessToken(String slug, String accessToken) {
    if (accessToken == null || !accessToken.contains("|")) {
      return false;
    }

    Optional<CollectionEntity> optCollection = collectionRepository.findBySlug(slug);
    if (optCollection.isEmpty()) {
      return false;
    }

    // Non-protected collections are always accessible
    if (optCollection.get().getPasswordHash() == null) {
      return true;
    }

    String[] parts = accessToken.split("\\|");
    if (parts.length != 2) {
      return false;
    }
    try {
      long expiry = Long.parseLong(parts[1]);
      if (Instant.now().getEpochSecond() > expiry) {
        return false;
      }
      String expectedHmac = computeHmac(slug + "|" + expiry, accessTokenSecret);
      return MessageDigest.isEqual(
          expectedHmac.getBytes(StandardCharsets.UTF_8), parts[0].getBytes(StandardCharsets.UTF_8));
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private String computeHmac(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    } catch (Exception e) {
      throw new RuntimeException("HMAC computation failed", e);
    }
  }
}
