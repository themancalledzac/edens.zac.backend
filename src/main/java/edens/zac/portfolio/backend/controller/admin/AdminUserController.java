package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.controller.admin.UserRequests.AdminUserCollection;
import edens.zac.portfolio.backend.controller.admin.UserRequests.AdminUserSummary;
import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserRequest;
import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserResponse;
import edens.zac.portfolio.backend.controller.admin.UserRequests.SetCollectionRoleRequest;
import edens.zac.portfolio.backend.controller.admin.UserRequests.UpdateUserRequest;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for user account management.
 *
 * <p>Authorization is perimeter-based, not role-based. In the {@code prod} profile every request is
 * gated by {@link edens.zac.portfolio.backend.config.InternalSecretFilter} on the {@code
 * X-Internal-Secret} header that the BFF proxy injects for same-origin admin calls. Spring
 * Security's authorization matrix does NOT gate these routes — {@link
 * edens.zac.portfolio.backend.config.SecurityConfig} falls through to {@code permitAll}, so outside
 * {@code prod} (e.g. local dev) they are unauthenticated. Per-user admin RBAC is deferred (Phase
 * A).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final AppUserRepository appUserRepository;
  private final UserInviteService userInviteService;
  private final UserCollectionRepository userCollectionRepository;
  private final UserPageAssembler userPageAssembler;
  private final String frontendBaseUrl;

  public AdminUserController(
      AppUserRepository appUserRepository,
      UserInviteService userInviteService,
      UserCollectionRepository userCollectionRepository,
      UserPageAssembler userPageAssembler,
      @Value("${email.frontend-base-url}") String frontendBaseUrl) {
    this.appUserRepository = appUserRepository;
    this.userInviteService = userInviteService;
    this.userCollectionRepository = userCollectionRepository;
    this.userPageAssembler = userPageAssembler;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  /**
   * Create a new user account in {@code INVITED} status and return a single-use invite URL for the
   * invitee to complete registration.
   *
   * <p>Returns {@code 409 Conflict} if the email already exists (checked before insert; a {@code
   * DataIntegrityViolationException} is handled by {@link
   * edens.zac.portfolio.backend.config.GlobalExceptionHandler} as belt-and-suspenders for races).
   *
   * @param request the new-user parameters; email is normalized to lowercase
   * @return {@code 201 Created} with {@link CreateUserResponse}
   */
  @PostMapping
  @Transactional
  public ResponseEntity<CreateUserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    String email = request.email().toLowerCase();

    if (appUserRepository.findByEmail(email).isPresent()) {
      log.warn("Admin create-user rejected: email already exists (email={})", email);
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    AppUserEntity newUser =
        AppUserEntity.builder()
            .email(email)
            .status(UserStatus.INVITED)
            .webauthnUserHandle(UUID.randomUUID())
            .name(request.displayName())
            .passwordHash(null)
            .build();

    Long userId = appUserRepository.insert(newUser);
    String rawToken = userInviteService.createInvite(userId, email);
    String inviteUrl = buildInviteUrl(rawToken);

    log.info("Admin created user (userId={}, email={})", userId, email);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreateUserResponse(userId, inviteUrl));
  }

  /**
   * List all user accounts (newest first) for the admin user-management view. Returns identity and
   * lifecycle state only — never password hashes or WebAuthn handles.
   *
   * @return the user summaries
   */
  @GetMapping
  public List<AdminUserSummary> listUsers() {
    return appUserRepository.findAllOrderedByCreatedAt().stream()
        .map(u -> new AdminUserSummary(u.getId(), u.getEmail(), u.getName(), u.getStatus()))
        .toList();
  }

  /**
   * Re-issue a single-use invite link for an existing user — a resend for an {@code INVITED} user,
   * a password-reset for an {@code ACTIVE} one (both complete the same accept flow). Invalidates
   * the user's prior unused invites so only the newest link is live; the account status is
   * unchanged.
   *
   * @param id the {@code app_user.id} to re-invite
   * @return {@code 200} with {@link CreateUserResponse}, or {@code 404} if no such user
   */
  @PostMapping("/{id}/invite")
  @Transactional
  public ResponseEntity<CreateUserResponse> regenerateInvite(@PathVariable Long id) {
    Optional<AppUserEntity> maybeUser = appUserRepository.findById(id);
    if (maybeUser.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    AppUserEntity user = maybeUser.get();
    String rawToken = userInviteService.regenerateInvite(user.getId(), user.getEmail());
    log.info("Admin regenerated invite (userId={})", user.getId());
    return ResponseEntity.ok(new CreateUserResponse(user.getId(), buildInviteUrl(rawToken)));
  }

  /**
   * Fetch a single user summary for the admin detail view.
   *
   * @param id the {@code app_user.id}
   * @return {@code 200} with {@link AdminUserSummary}, or {@code 404} if no such user
   */
  @GetMapping("/{id}")
  public ResponseEntity<AdminUserSummary> getUser(@PathVariable Long id) {
    return appUserRepository
        .findById(id)
        .map(
            u ->
                ResponseEntity.ok(
                    new AdminUserSummary(u.getId(), u.getEmail(), u.getName(), u.getStatus())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Update the two admin-editable fields (display name + status). Email is immutable here.
   *
   * @param id the {@code app_user.id}
   * @param request the new display name (nullable) and status (required)
   * @return {@code 200} with the refreshed {@link AdminUserSummary}, or {@code 404} if no such user
   */
  @PatchMapping("/{id}")
  @Transactional
  public ResponseEntity<AdminUserSummary> updateUser(
      @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
    if (appUserRepository.findById(id).isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    appUserRepository.updateName(id, request.displayName());
    appUserRepository.updateStatus(id, request.status());
    AppUserEntity updated = appUserRepository.findById(id).orElseThrow();
    return ResponseEntity.ok(
        new AdminUserSummary(
            updated.getId(), updated.getEmail(), updated.getName(), updated.getStatus()));
  }

  /**
   * Collections the user is associated with (tagged via {@code collection_people} or holding a
   * {@code user_collection} membership), with current role. Role is {@code null} when tagged only.
   *
   * @param id the {@code app_user.id}
   * @return the associated collections
   */
  @GetMapping("/{id}/collections")
  public ResponseEntity<List<AdminUserCollection>> userCollections(@PathVariable Long id) {
    List<AdminUserCollection> rows =
        userCollectionRepository.findAssociatedCollections(id).stream()
            .map(a -> new AdminUserCollection(a.collectionId(), a.title(), a.role()))
            .toList();
    return ResponseEntity.ok(rows);
  }

  /**
   * Set the user's membership role on a collection (GENERAL or CLIENT). Creates the membership row
   * if absent; promotes/demotes if already present.
   *
   * @param id the {@code app_user.id}
   * @param collectionId the collection id
   * @param body the new role
   * @return {@code 204 No Content}
   */
  @PutMapping("/{id}/collections/{collectionId}")
  public ResponseEntity<Void> setCollectionRole(
      @PathVariable Long id,
      @PathVariable Long collectionId,
      @Valid @RequestBody SetCollectionRoleRequest body) {
    userCollectionRepository.upsertRole(id, collectionId, body.role(), null);
    return ResponseEntity.noContent().build();
  }

  /**
   * Remove the user's membership on a collection (revoke all access).
   *
   * @param id the {@code app_user.id}
   * @param collectionId the collection id
   * @return {@code 204 No Content}
   */
  @DeleteMapping("/{id}/collections/{collectionId}")
  public ResponseEntity<Void> removeCollectionRole(
      @PathVariable Long id, @PathVariable Long collectionId) {
    userCollectionRepository.delete(id, collectionId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Admin view of a user's full page (what they see at /user). Perimeter-gated.
   *
   * @param id the {@code app_user.id}
   * @return the assembled {@link CollectionModel}
   */
  @GetMapping("/{id}/page")
  public ResponseEntity<CollectionModel> userPage(@PathVariable Long id) {
    return ResponseEntity.ok(userPageAssembler.assembleForUser(id));
  }

  /** Build the public invite URL, tolerating a {@code frontendBaseUrl} that ends in a slash. */
  private String buildInviteUrl(String rawToken) {
    return frontendBaseUrl.replaceAll("/+$", "") + "/invite/" + rawToken;
  }
}
