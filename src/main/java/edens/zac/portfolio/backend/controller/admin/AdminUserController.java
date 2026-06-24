package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserRequest;
import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserResponse;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for user account management. Secured behind the BFF perimeter — callers must hold
 * an authenticated admin session; Spring Security's authorization matrix enforces this.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final AppUserRepository appUserRepository;
  private final UserInviteService userInviteService;
  private final String frontendBaseUrl;

  public AdminUserController(
      AppUserRepository appUserRepository,
      UserInviteService userInviteService,
      @Value("${email.frontend-base-url}") String frontendBaseUrl) {
    this.appUserRepository = appUserRepository;
    this.userInviteService = userInviteService;
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
            .role(Role.CLIENT)
            .status(UserStatus.INVITED)
            .webauthnUserHandle(UUID.randomUUID())
            .displayName(request.displayName())
            .passwordHash(null)
            .build();

    Long userId = appUserRepository.insert(newUser);
    String rawToken = userInviteService.createInvite(userId, email);
    String inviteUrl = frontendBaseUrl + "/invite/" + rawToken;

    log.info("Admin created user (userId={}, email={})", userId, email);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreateUserResponse(userId, inviteUrl));
  }
}
