package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
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
    if (collection.getGalleryPassword() == null) {
      return true;
    }

    // Password required but not provided
    if (password == null || password.isEmpty()) {
      return false;
    }

    return collection.getGalleryPassword().equals(password);
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
    if (optCollection.get().getGalleryPassword() == null) {
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

  /**
   * Stable, opaque fingerprint of a gallery password. Two galleries with the same password produce
   * the same fingerprint, enabling shared-unlock cookies across a "password group" without storing
   * any group identifier. Computed via HMAC keyed by {@code accessTokenSecret} so the fingerprint
   * is not derivable without the server secret.
   *
   * @param password The plaintext gallery password
   * @return URL-safe base64 fingerprint, or {@code null} when {@code password} is null/blank
   */
  public String passwordFingerprint(String password) {
    if (password == null || password.isEmpty()) {
      return null;
    }
    return computeHmac("pwfp:" + password, accessTokenSecret);
  }

  /**
   * Generate a time-limited HMAC access token bound to a password fingerprint. Issued alongside the
   * per-slug token at unlock so any gallery sharing the same password also unlocks.
   *
   * @param password The plaintext gallery password (must be non-null/non-empty)
   * @return HMAC token with embedded expiry
   */
  public String generatePasswordAccessToken(String password) {
    String fingerprint = passwordFingerprint(password);
    long expiry = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
    String payload = "pw:" + fingerprint + "|" + expiry;
    String hmac = computeHmac(payload, accessTokenSecret);
    return hmac + "|" + expiry;
  }

  /**
   * Validate a password-fingerprint access token against the gallery's current password. Returns
   * false (gate) when the password is null/blank — fingerprint cookies are only meaningful for
   * protected galleries.
   *
   * @param password The current plaintext password of the gallery being read
   * @param accessToken The token from the {@code gallery_access_pw_<fingerprint>} cookie
   * @return true if valid, fingerprint matches, and not expired
   */
  public boolean validatePasswordAccessToken(String password, String accessToken) {
    if (password == null || password.isEmpty()) {
      return false;
    }
    if (accessToken == null || !accessToken.contains("|")) {
      return false;
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
      String fingerprint = passwordFingerprint(password);
      String expectedHmac = computeHmac("pw:" + fingerprint + "|" + expiry, accessTokenSecret);
      return MessageDigest.isEqual(
          expectedHmac.getBytes(StandardCharsets.UTF_8), parts[0].getBytes(StandardCharsets.UTF_8));
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Build the {@code Set-Cookie} cookies issued on a successful gallery unlock. Always includes the
   * per-slug access cookie. When {@code password} is non-blank, additionally includes the shared
   * password-fingerprint cookie so any other gallery whose password produces the same fingerprint
   * also passes the gate without re-prompting.
   *
   * <p>Centralizing cookie construction here keeps controllers focused on HTTP wiring and prevents
   * drift between the cookie name/value/attributes used at unlock and the names looked up in {@link
   * GalleryAccessCookies#hasValidAccess(jakarta.servlet.http.HttpServletRequest, String, String,
   * ClientGalleryAuthService)}.
   */
  public List<ResponseCookie> buildAccessCookies(
      String slug, String password, boolean secure, Duration maxAge) {
    List<ResponseCookie> cookies = new ArrayList<>(2);
    cookies.add(
        ResponseCookie.from(GalleryAccessCookies.cookieName(slug), generateAccessToken(slug))
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build());

    String fingerprint = passwordFingerprint(password);
    if (fingerprint != null) {
      cookies.add(
          ResponseCookie.from(
                  GalleryAccessCookies.passwordCookieName(fingerprint),
                  generatePasswordAccessToken(password))
              .httpOnly(true)
              .secure(secure)
              .sameSite("Strict")
              .path("/")
              .maxAge(maxAge)
              .build());
    }
    return cookies;
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
