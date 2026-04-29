package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.services.CollectionProcessingUtil;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint that atomically sets the per-gallery password and emails it to the recipient.
 *
 * <p>BCrypt is one-way -- you can never re-derive a stored password -- so the admin "send password"
 * action takes the plaintext in the request body, hashes it onto the {@link CollectionEntity}, and
 * emails the same plaintext through {@link EmailService}. The two operations run in the same
 * request so the plaintext is never persisted.
 *
 * <p>Mounted under {@code /api/admin/...} and runs in dev and prod (no {@code @Profile} gating); in
 * prod, access is gated by {@link edens.zac.portfolio.backend.config.InternalSecretFilter}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/collections")
public class CollectionAdminEmailController {

  private final CollectionService collectionService;
  private final EmailService emailService;

  public record SendPasswordRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String password) {}

  public record SendPasswordResponse(boolean sent, String reason) {}

  /**
   * Set the gallery password (BCrypt-hashed) and email the plaintext to the client.
   *
   * <p>Returns {@code {sent, reason}} where {@code reason} is null on success, {@code
   * "email-disabled"} when the {@code email.enabled} flag is off (password still updated), {@code
   * "ses-error"} when SES rejected the request, or {@code "not-client-gallery"} / {@code
   * "no-collection-title"} for the input validations.
   */
  // TODO: add admin-side send rate limit to protect SES quota
  @PostMapping("/{id}/send-password")
  public ResponseEntity<SendPasswordResponse> sendPassword(
      @PathVariable Long id, @Valid @RequestBody SendPasswordRequest request) {
    CollectionEntity collection = collectionService.findEntityById(id);

    if (collection.getType() != CollectionType.CLIENT_GALLERY) {
      log.warn(
          "Refusing send-password on non-CLIENT_GALLERY collection (id={}, type={})",
          id,
          collection.getType());
      return ResponseEntity.badRequest()
          .body(new SendPasswordResponse(false, "not-client-gallery"));
    }

    // Hash + persist. Plaintext stays in the request body only.
    collection.setPasswordHash(CollectionProcessingUtil.hashPassword(request.password()));
    collectionService.saveEntity(collection);
    log.info("Password updated for client gallery (id={}, slug={})", id, collection.getSlug());

    EmailService.SendResult result =
        emailService.sendGalleryPasswordEmail(
            request.email(), collection.getTitle(), collection.getSlug(), request.password());

    return ResponseEntity.ok(new SendPasswordResponse(result.sent(), result.reason()));
  }
}
