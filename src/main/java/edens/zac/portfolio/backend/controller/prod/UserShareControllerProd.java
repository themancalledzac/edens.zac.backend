package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ShareModels;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionProcessingUtil;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner side of a share link: session-required, self-only. No route accepts a user id -- the
 * principal is the only subject, mirroring {@link UserControllerProd}.
 *
 * <p>Lives under {@code /api/read/**}, which {@code SecurityConfig} leaves {@code permitAll}, so
 * identity is enforced here with {@code AuthPrincipal.isRealUser} rather than by a matcher. That
 * check also excludes a share-link holder, which matters: a recipient reaching these routes could
 * otherwise reset the very link they are browsing on, or read the owner's grant candidates.
 */
@RestController
@RequestMapping("/api/read/user/share")
@RequiredArgsConstructor
@Slf4j
public class UserShareControllerProd {

  private final ShareLinkService shareLinkService;
  private final CollectionAccessService collectionAccessService;
  private final CollectionRepository collectionRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final AppUserRepository appUserRepository;
  private final EmailService emailService;

  @Value("${email.frontend-base-url}")
  private String frontendBaseUrl;

  /**
   * The owner's current link state, including the live token so the page can render a copyable
   * link. Recovering it needs the server secret, not just the database -- see {@code TokenCipher}.
   */
  @GetMapping
  public ResponseEntity<ShareModels.ShareSettings> settings(
      @AuthenticationPrincipal AuthPrincipal principal) {
    if (!AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(buildSettings(principal.userId(), null));
  }

  /**
   * Mint the link, or reset it. One endpoint for both because they are the same operation from the
   * owner's point of view -- "give me a link that works" -- and the same operation underneath: a
   * fresh token on the same row. Resetting invalidates the previously shared URL and every cookie
   * minted from it, while keeping the opt-in collections.
   *
   * <p>This is the only destructive route here, and the UI should say so plainly: anyone already
   * holding the old link loses access the moment it is called. Re-sending to a new person does NOT
   * require it -- {@link #settings} returns the live link for exactly that reason.
   */
  @PostMapping("/rotate")
  public ResponseEntity<ShareModels.ShareSettings> rotate(
      @AuthenticationPrincipal AuthPrincipal principal) {
    if (!AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String raw = shareLinkService.mintOrRotate(principal.userId());
    log.info("Share link minted or rotated for user {}", principal.userId());
    return ResponseEntity.ok(buildSettings(principal.userId(), raw));
  }

  /**
   * Email the sender's own link to someone.
   *
   * <p>Sends the link already in circulation rather than minting a fresh one, so emailing a second
   * person does not invalidate the first person's copy. A link that cannot be recovered (minted
   * before V57) is a 409: the honest answer is "reset to get a new one", not a silent no-op.
   *
   * <p>Delivery is best-effort and never fails the request. {@link EmailService} returns a typed
   * reason instead of throwing, and short-circuits while {@code email.enabled} is false -- so the
   * copy-link flow behaves identically whether or not email is switched on. No transaction hook is
   * needed here (unlike the invite flow) because nothing is written: the token already exists and a
   * rollback cannot erase it.
   */
  @PostMapping("/email")
  public ResponseEntity<ShareModels.ShareEmailResult> emailLink(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody ShareModels.SendShareLinkRequest body) {
    if (!AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Optional<String> token = shareLinkService.revealToken(principal.userId());
    if (token.isEmpty()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    String ownerName =
        appUserRepository.findById(principal.userId()).map(AppUserEntity::getName).orElse(null);
    EmailService.SendResult result =
        emailService.sendShareLinkEmail(body.toEmail(), ownerName, buildShareUrl(token.get()));
    // The address is logged, the link never is.
    log.info("Share link email requested by user {} (sent={})", principal.userId(), result.sent());
    return ResponseEntity.ok(new ShareModels.ShareEmailResult(result.sent(), result.reason()));
  }

  /** Byte-identical to the link the owner copies, matching how the invite flow builds its URL. */
  private String buildShareUrl(String rawToken) {
    return frontendBaseUrl.replaceAll("/+$", "") + "/s/" + rawToken;
  }

  /**
   * Add a role-granted collection to the share.
   *
   * <p>Authorized on the owner still holding a grant on that collection, so the toggle cannot be
   * used to widen a share beyond what its owner can see. Opting in something you cannot view is a
   * 403 rather than a silent no-op, because the alternative -- accepting the row and quietly
   * dropping it from the scope -- would leave the owner believing they had shared something.
   */
  @PutMapping("/collections/{collectionId}")
  public ResponseEntity<Void> addCollection(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long collectionId) {
    if (!AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!collectionAccessService.canView(principal.userId(), collectionId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    Optional<ShareLinkEntity> link = shareLinkService.findForUser(principal.userId());
    if (link.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    shareLinkService.addOptIn(link.get().getId(), collectionId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Remove a collection from the share. Deliberately NOT gated on the owner's current grant: if
   * their access was revoked, they must still be able to take it out of their share.
   */
  @DeleteMapping("/collections/{collectionId}")
  public ResponseEntity<Void> removeCollection(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long collectionId) {
    if (!AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Optional<ShareLinkEntity> link = shareLinkService.findForUser(principal.userId());
    if (link.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    shareLinkService.removeOptIn(link.get().getId(), collectionId);
    return ResponseEntity.noContent().build();
  }

  /**
   * @param freshToken the token just minted by a rotate, or null on a plain read -- in which case
   *     the owner's live token is decrypted from storage so they can copy or re-send the link they
   *     already gave out, without resetting it and breaking the recipient already using it
   */
  private ShareModels.ShareSettings buildSettings(Long userId, String freshToken) {
    Optional<ShareLinkEntity> link = shareLinkService.findForUser(userId);
    List<Long> optedIn =
        link.map(l -> shareLinkService.optInCollectionIds(l.getId())).orElseGet(List::of);
    String token =
        freshToken != null ? freshToken : shareLinkService.revealToken(userId).orElse(null);
    return new ShareModels.ShareSettings(
        link.isPresent(),
        token,
        link.map(ShareLinkEntity::getCreatedAt).orElse(null),
        link.map(ShareLinkEntity::getRotatedAt).orElse(null),
        link.map(ShareLinkEntity::getLastUsedAt).orElse(null),
        optedIn,
        candidateCollections(userId));
  }

  /**
   * Collections the owner could add: the ones they reach through a role grant but are NOT tagged
   * in. Tagged-in collections are excluded because they are already in every share by default, so
   * offering them as toggles would imply they could be removed, which this slice does not support.
   */
  private List<CollectionModel> candidateCollections(Long userId) {
    Set<Long> candidates =
        new LinkedHashSet<>(collectionAccessService.memberCollectionIdsForUser(userId));
    candidates.removeAll(collectionRepository.findCollectionIdsByPersonId(userId));
    if (candidates.isEmpty()) {
      return List.of();
    }
    List<CollectionEntity> rows = collectionRepository.findByIds(List.copyOf(candidates));
    return collectionProcessingUtil.batchConvertToBasicModels(rows);
  }
}
