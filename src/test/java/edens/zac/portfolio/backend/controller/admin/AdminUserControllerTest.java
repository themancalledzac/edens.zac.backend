package edens.zac.portfolio.backend.controller.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.services.RoleGrantPropagationService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.UserFollowsService;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.services.UserMergeService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.services.UserSavesService;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

  private MockMvc mockMvc;

  @Mock private AppUserRepository appUserRepository;
  @Mock private UserInviteService userInviteService;
  @Mock private RoleRepository roleRepository;
  @Mock private UserPageAssembler userPageAssembler;
  @Mock private UserSavesService userSavesService;
  @Mock private UserFollowsService userFollowsService;
  @Mock private UserMergeService userMergeService;
  @Mock private EmailService emailService;
  @Mock private SessionService sessionService;
  @Mock private WebAuthnCredentialRepository credentialRepository;
  @Mock private RoleGrantPropagationService roleGrantPropagationService;

  /** Trailing slash on purpose: exercises the trailing-slash-safe invite-URL join. */
  private static final String FRONTEND_BASE_URL = "https://app.example.com/";

  /**
   * The role-membership routes delegate to a real {@link AdminRoleController} over the same mocked
   * {@link RoleRepository}, so the delegation itself is under test rather than stubbed away.
   */
  @BeforeEach
  void setUp() {
    AdminRoleController adminRoleController =
        new AdminRoleController(roleRepository, roleGrantPropagationService);
    AdminUserController controller =
        new AdminUserController(
            appUserRepository,
            userInviteService,
            roleRepository,
            userPageAssembler,
            userSavesService,
            userFollowsService,
            userMergeService,
            emailService,
            sessionService,
            credentialRepository,
            adminRoleController,
            FRONTEND_BASE_URL);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class CreateUser {

    /**
     * A trailing slash on the base URL must not produce a double slash before {@code invite}, and
     * the invitee is emailed the same link that is returned to the admin for copy-linking.
     */
    @Test
    void createUserReturns201WithUserIdAndInviteUrl() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
      when(appUserRepository.insert(any(AppUserEntity.class))).thenReturn(42L);
      when(userInviteService.createInvite(42L, "alice@example.com")).thenReturn("raw-token-abc");

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"alice@example.com\",\"displayName\":\"Alice\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.userId").value(42))
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/raw-token-abc"));

      verify(emailService)
          .sendInviteEmail(
              "alice@example.com", "Alice", "https://app.example.com/invite/raw-token-abc");
    }

    @Test
    void createUserDoesNotEmailWhenEmailAlreadyExists() throws Exception {
      when(appUserRepository.findByEmail("taken@example.com"))
          .thenReturn(
              Optional.of(AppUserEntity.builder().id(1L).email("taken@example.com").build()));

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"taken@example.com\",\"displayName\":\"Taken\"}"))
          .andExpect(status().isConflict());

      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    @Test
    void emailIsNormalizedToLowercase() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
      when(appUserRepository.insert(any(AppUserEntity.class))).thenReturn(7L);
      when(userInviteService.createInvite(7L, "alice@example.com")).thenReturn("tok");

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"ALICE@EXAMPLE.COM\"}"))
          .andExpect(status().isCreated());

      verify(appUserRepository).findByEmail("alice@example.com");
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com"))
          .thenReturn(Optional.of(AppUserEntity.builder().id(1L).build()));

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"alice@example.com\"}"))
          .andExpect(status().isConflict());

      verify(appUserRepository, never()).insert(any());
      verify(userInviteService, never()).createInvite(anyLong(), anyString());
    }

    @Test
    void missingEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"NoEmail\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\"}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class ListUsers {

    /** Sensitive fields must never be serialized into the admin list. */
    @Test
    void listUsersReturnsSummariesWithoutSensitiveFields() throws Exception {
      AppUserEntity alice =
          AppUserEntity.builder()
              .id(1L)
              .email("alice@example.com")
              .name("Alice")
              .description("A keen landscape photographer.")
              .status(UserStatus.ACTIVE)
              .passwordHash("{bcrypt}$2a$10$secret")
              .webauthnUserHandle(UUID.randomUUID())
              .build();
      AppUserEntity bob =
          AppUserEntity.builder()
              .id(2L)
              .email("bob@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findAllOrderedByCreatedAt()).thenReturn(List.of(alice, bob));

      mockMvc
          .perform(get("/api/admin/users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].email").value("alice@example.com"))
          .andExpect(jsonPath("$[0].displayName").value("Alice"))
          .andExpect(jsonPath("$[0].description").value("A keen landscape photographer."))
          .andExpect(jsonPath("$[0].status").value("ACTIVE"))
          .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
          .andExpect(jsonPath("$[0].webauthnUserHandle").doesNotExist())
          .andExpect(jsonPath("$[1].status").value("INVITED"));
    }

    /**
     * V35 merged {@code content_people} into {@code users} as {@code status=PERSON} rows: tag-only
     * identities with no account. The admin account list must skip them, and must not 400 on the
     * new enum value.
     */
    @Test
    void listUsersExcludesPersonTagRows() throws Exception {
      AppUserEntity account =
          AppUserEntity.builder()
              .id(1L)
              .email("alice@example.com")
              .name("Alice")
              .status(UserStatus.ACTIVE)
              .build();
      AppUserEntity personTag =
          AppUserEntity.builder().id(2L).name("Abby Bennett").status(UserStatus.PERSON).build();
      when(appUserRepository.findAllOrderedByCreatedAt()).thenReturn(List.of(account, personTag));

      mockMvc
          .perform(get("/api/admin/users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
  }

  @Nested
  class RegenerateInvite {

    /**
     * A re-issued invite is emailed too, so a resend reaches the invitee without the admin having
     * to copy the link by hand.
     */
    @Test
    void regenerateReturns200WithFreshInviteUrl() throws Exception {
      AppUserEntity bob =
          AppUserEntity.builder()
              .id(5L)
              .email("bob@example.com")
              .name("Bob")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(bob));
      when(userInviteService.regenerateInvite(5L, "bob@example.com")).thenReturn("fresh-token");

      mockMvc
          .perform(post("/api/admin/users/5/invite"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(5))
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/fresh-token"));

      verify(emailService)
          .sendInviteEmail("bob@example.com", "Bob", "https://app.example.com/invite/fresh-token");
    }

    @Test
    void regenerateUnknownUserReturns404() throws Exception {
      when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

      mockMvc.perform(post("/api/admin/users/999/invite")).andExpect(status().isNotFound());

      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    /**
     * The admin-issued password reset. ACTIVE is on the {@code mayAcceptInvite} allowlist and the
     * gate must not close it -- this is the second of the two statuses this endpoint exists to
     * serve.
     */
    @Test
    void regenerateForActiveUserReturns200() throws Exception {
      AppUserEntity carol =
          AppUserEntity.builder()
              .id(6L)
              .email("carol@example.com")
              .name("Carol")
              .status(UserStatus.ACTIVE)
              .build();
      when(appUserRepository.findById(6L)).thenReturn(Optional.of(carol));
      when(userInviteService.regenerateInvite(6L, "carol@example.com")).thenReturn("reset-token");

      mockMvc
          .perform(post("/api/admin/users/6/invite"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/reset-token"));
    }

    /**
     * S-21: {@code accept()} refuses DISABLED, so without this gate the admin got a 200 and a URL,
     * the invitee got the email, clicked it, and received 410 Gone, with nothing anywhere saying
     * the account was ineligible.
     */
    @Test
    void regenerateForDisabledUserReturns409AndMintsNothing() throws Exception {
      AppUserEntity dana =
          AppUserEntity.builder()
              .id(7L)
              .email("dana@example.com")
              .name("Dana")
              .status(UserStatus.DISABLED)
              .build();
      when(appUserRepository.findById(7L)).thenReturn(Optional.of(dana));

      mockMvc.perform(post("/api/admin/users/7/invite")).andExpect(status().isConflict());

      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    /**
     * S-21, the louder half. A PERSON row's {@code users.email} is NULL while {@code
     * user_invite.email} is NOT NULL (V32), so the insert raised {@code
     * DataIntegrityViolationException} and {@code GlobalExceptionHandler} reported "duplicate or
     * invalid data" -- a schema constraint doing a status check's job and naming the wrong reason.
     * The gate now answers first and the insert is never attempted.
     */
    @Test
    void regenerateForPersonReturns409BeforeTheSchemaRejectsIt() throws Exception {
      AppUserEntity person =
          AppUserEntity.builder().id(8L).email(null).name("Evan").status(UserStatus.PERSON).build();
      when(appUserRepository.findById(8L)).thenReturn(Optional.of(person));

      mockMvc.perform(post("/api/admin/users/8/invite")).andExpect(status().isConflict());

      verify(userInviteService, never()).regenerateInvite(anyLong(), any());
      verify(emailService, never()).sendInviteEmail(any(), any(), anyString());
    }
  }

  @Nested
  class UpgradeUser {

    private AppUserEntity person(Long id) {
      return AppUserEntity.builder().id(id).name("Abby Bennett").status(UserStatus.PERSON).build();
    }

    @Test
    void upgradeReturns200WithUserIdAndInviteUrl() throws Exception {
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(person(5L)));
      when(appUserRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
      when(userInviteService.regenerateInvite(5L, "person@example.com")).thenReturn("raw-token");

      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"person@example.com\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(5))
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/raw-token"));

      verify(appUserRepository).updateEmail(5L, "person@example.com");
      verify(appUserRepository).updateStatus(5L, UserStatus.INVITED);
    }

    /**
     * Upgrade is an invite-minting endpoint like create-user and regenerate-invite, so it must
     * deliver the link too. The person's existing tag name is the greeting name.
     */
    @Test
    void upgradeEmailsTheInviteToTheUpgradedPerson() throws Exception {
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(person(5L)));
      when(appUserRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
      when(userInviteService.regenerateInvite(5L, "person@example.com")).thenReturn("raw-token");

      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"person@example.com\"}"))
          .andExpect(status().isOk());

      verify(emailService)
          .sendInviteEmail(
              "person@example.com", "Abby Bennett", "https://app.example.com/invite/raw-token");
    }

    /** Both the duplicate check and the write must see the lowercased email. */
    @Test
    void upgradeNormalizesEmailToLowercase() throws Exception {
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(person(5L)));
      when(appUserRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
      when(userInviteService.regenerateInvite(5L, "person@example.com")).thenReturn("raw-token");

      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"PERSON@EXAMPLE.COM\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).findByEmail("person@example.com");
      verify(appUserRepository).updateEmail(5L, "person@example.com");
    }

    @Test
    void upgradeUnknownUserReturns404() throws Exception {
      when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

      mockMvc
          .perform(
              post("/api/admin/users/999/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"person@example.com\"}"))
          .andExpect(status().isNotFound());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    /**
     * Only a tag-only PERSON can be upgraded. An ACTIVE, INVITED or DISABLED row is already an
     * account, so upgrading it -- clobbering its email and resetting its status -- is a conflict.
     */
    @Test
    void upgradeNonPersonUserReturns409() throws Exception {
      for (UserStatus status :
          new UserStatus[] {UserStatus.ACTIVE, UserStatus.INVITED, UserStatus.DISABLED}) {
        AppUserEntity account =
            AppUserEntity.builder().id(5L).email("real@example.com").status(status).build();
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(account));

        mockMvc
            .perform(
                post("/api/admin/users/5/upgrade")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"person@example.com\"}"))
            .andExpect(status().isConflict());
      }

      verify(appUserRepository, never()).findByEmail(anyString());
      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    /**
     * 409 is a normal return, so {@code @Transactional} commits this path: no field may have been
     * written by the time the conflict is detected, or a partial upgrade would persist.
     */
    @Test
    void upgradeEmailOwnedByAnotherUserReturns409() throws Exception {
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(person(5L)));
      when(appUserRepository.findByEmail("person@example.com"))
          .thenReturn(Optional.of(AppUserEntity.builder().id(1L).build()));

      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"person@example.com\"}"))
          .andExpect(status().isConflict());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
      verify(emailService, never()).sendInviteEmail(anyString(), any(), anyString());
    }

    @Test
    void upgradeMissingEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).findById(anyLong());
      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
    }

    @Test
    void upgradeInvalidEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\"}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).findById(anyLong());
    }

    /** The person's tagged name is preserved as the account display name, never overwritten. */
    @Test
    void upgradeFlipsStatusToInvitedAndLeavesNameUntouched() throws Exception {
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(person(5L)));
      when(appUserRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
      when(userInviteService.regenerateInvite(5L, "person@example.com")).thenReturn("raw-token");

      mockMvc
          .perform(
              post("/api/admin/users/5/upgrade")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"person@example.com\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).updateStatus(5L, UserStatus.INVITED);
      verify(appUserRepository, never()).updateName(anyLong(), anyString());
    }
  }

  @Nested
  class GetUser {

    @Test
    void getUserReturnsSummary() throws Exception {
      AppUserEntity user =
          AppUserEntity.builder()
              .id(3L)
              .email("carol@example.com")
              .name("Carol")
              .description("Documentary photographer based in Seattle.")
              .status(UserStatus.ACTIVE)
              .passwordHash("secret-hash")
              .webauthnUserHandle(UUID.randomUUID())
              .build();
      when(appUserRepository.findById(3L)).thenReturn(Optional.of(user));

      mockMvc
          .perform(get("/api/admin/users/3"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(3))
          .andExpect(jsonPath("$.email").value("carol@example.com"))
          .andExpect(jsonPath("$.displayName").value("Carol"))
          .andExpect(jsonPath("$.description").value("Documentary photographer based in Seattle."))
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.passwordHash").doesNotExist())
          .andExpect(jsonPath("$.webauthnUserHandle").doesNotExist());
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
      when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

      mockMvc.perform(get("/api/admin/users/999")).andExpect(status().isNotFound());
    }
  }

  @Nested
  class UpdateUser {

    @Test
    void updateUserAppliesDisplayNameAndStatusAndReturnsSummary() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Ken")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Kenneth")
              .status(UserStatus.ACTIVE)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Kenneth\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(8))
          .andExpect(jsonPath("$.displayName").value("Kenneth"))
          .andExpect(jsonPath("$.status").value("ACTIVE"));

      verify(appUserRepository).updateName(8L, "Kenneth");
      verify(appUserRepository).updateStatus(8L, UserStatus.ACTIVE);
      verify(appUserRepository).updateDescription(8L, null);
    }

    @Test
    void updateUserWithDescriptionPersistsAndEchoes() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(9L)
              .email("diana@example.com")
              .name("Diana")
              .status(UserStatus.ACTIVE)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(9L)
              .email("diana@example.com")
              .name("Diana")
              .description("Wildlife and conservation photographer.")
              .status(UserStatus.ACTIVE)
              .build();
      when(appUserRepository.findById(9L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/9")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"displayName\":\"Diana\",\"status\":\"ACTIVE\","
                          + "\"description\":\"Wildlife and conservation photographer.\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.description").value("Wildlife and conservation photographer."));

      verify(appUserRepository).updateDescription(9L, "Wildlife and conservation photographer.");
    }

    @Test
    void updateUnknownUserReturns404() throws Exception {
      when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

      mockMvc
          .perform(
              patch("/api/admin/users/999")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Nobody\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isNotFound());

      verify(appUserRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    void updateWithMissingStatusReturns400() throws Exception {
      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Kenneth\"}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).updateName(anyLong(), anyString());
    }

    @Test
    void updateUserChangesEmailAndReturnsSummary() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Ken")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("kenneth@example.com")
              .name("Ken")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));
      when(appUserRepository.findByEmail("kenneth@example.com")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"kenneth@example.com\",\"displayName\":\"Ken\","
                          + "\"status\":\"INVITED\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("kenneth@example.com"));

      verify(appUserRepository).updateEmail(8L, "kenneth@example.com");
    }

    @Test
    void updateUserWithEmailOwnedByAnotherUserReturns409() throws Exception {
      AppUserEntity ken =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L)).thenReturn(Optional.of(ken));
      when(appUserRepository.findByEmail("alice@example.com"))
          .thenReturn(Optional.of(AppUserEntity.builder().id(1L).build()));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"alice@example.com\",\"status\":\"INVITED\"}"))
          .andExpect(status().isConflict());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).updateName(anyLong(), any());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(appUserRepository, never()).updateDescription(anyLong(), any());
    }

    /** Both the duplicate check and the write must see the lowercased email. */
    @Test
    void updateEmailIsNormalizedToLowercase() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("kenneth@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));
      when(appUserRepository.findByEmail("kenneth@example.com")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"KENNETH@EXAMPLE.COM\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).findByEmail("kenneth@example.com");
      verify(appUserRepository).updateEmail(8L, "kenneth@example.com");
    }

    /**
     * The frontend always sends the email field, so an unchanged email -- possibly re-cased -- is
     * the common path. The duplicate check must not trip on the user's own row.
     */
    @Test
    void resubmittingOwnEmailWithDifferentCaseReturns200NotConflict() throws Exception {
      AppUserEntity ken =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L)).thenReturn(Optional.of(ken));
      when(appUserRepository.findByEmail("ken@example.com")).thenReturn(Optional.of(ken));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"KEN@EXAMPLE.COM\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("ken@example.com"));

      verify(appUserRepository).updateEmail(8L, "ken@example.com");
    }

    @Test
    void updateWithoutEmailFieldLeavesEmailUntouched() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Ken")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Kenneth")
              .status(UserStatus.ACTIVE)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Kenneth\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("ken@example.com"));

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).findByEmail(anyString());
    }

    /**
     * Pins the {@code @Email} constraint on {@code UpdateUserRequest}: bean validation rejects the
     * body before the controller body runs, so no {@code findById} stub is needed and nothing is
     * written.
     */
    @Test
    void updateWithMalformedEmailReturns400() throws Exception {
      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\",\"status\":\"INVITED\"}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
    }

    /**
     * Pins {@code @Size(max = 500)} on {@code UpdateUserRequest.description}. The description is
     * admin-authored free text with no other length check between it and the column.
     *
     * <p>The row is stubbed {@code lenient()} for the reason {@link
     * #updateWithPersonStatusReturns400AndWritesNothing} gives: it is never read, and stubbing it
     * is what makes dropping the constraint land as a 200 that writes 501 characters rather than a
     * 404 on an unstubbed lookup.
     */
    @Test
    void updateWithOverlongDescriptionReturns400() throws Exception {
      AppUserEntity existing =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      lenient().when(appUserRepository.findById(8L)).thenReturn(Optional.of(existing));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"ACTIVE\",\"description\":\"" + "d".repeat(501) + "\"}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).updateDescription(anyLong(), anyString());
    }

    /**
     * S-13: PERSON is a tag-only identity, not an account lifecycle state. Writing it here would
     * make {@code PersonRepository.deletePersonById} -- which hard-deletes on {@code AND status =
     * 'PERSON'} -- match a real account, and would leave that account's {@code role_member} rows on
     * a person. Rejection is at the input, so nothing in the controller body runs: no sweep, no
     * write.
     *
     * <p>The row is stubbed {@code lenient()} on purpose. It is never read, and stubbing it is what
     * makes the mutation land where it matters: drop {@code @AccountStatus} and this PATCH succeeds
     * with 200 and writes PERSON, rather than 404-ing on an unstubbed lookup.
     */
    @Test
    void updateWithPersonStatusReturns400AndWritesNothing() throws Exception {
      AppUserEntity existing =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      lenient().when(appUserRepository.findById(8L)).thenReturn(Optional.of(existing));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"PERSON\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message", containsString("not an account status")));

      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(appUserRepository, never()).updateName(anyLong(), anyString());
      verify(roleRepository, never()).dropMembershipsIfPerson(anyLong());
    }

    /**
     * The other direction stays open: PATCHing a PERSON to an account status is how a person
     * becomes an account outside {@code upgradeUser}, and S-12's sweep depends on it running.
     * Constraining the request enum must not close it.
     */
    @Test
    void updatePersonToActiveStillSucceeds() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder().id(8L).name("Dana").status(UserStatus.PERSON).build();
      AppUserEntity after =
          AppUserEntity.builder().id(8L).name("Dana").status(UserStatus.ACTIVE).build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Dana\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status", is("ACTIVE")));

      verify(roleRepository).dropMembershipsIfPerson(8L);
      verify(appUserRepository).updateStatus(8L, UserStatus.ACTIVE);
    }

    /**
     * Account-takeover guard: an INVITED user has an outstanding invite bound to their old address.
     * When the admin corrects the email, the old link must die so whoever holds it -- the prior
     * address's inbox, say -- can no longer redeem it onto the corrected account.
     */
    @Test
    void changingInvitedUserEmailInvalidatesOutstandingInvite() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("kenneth@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));
      when(appUserRepository.findByEmail("kenneth@example.com")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"kenneth@example.com\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).updateEmail(8L, "kenneth@example.com");
      verify(userInviteService).invalidateInvites(8L);
    }

    /**
     * Scope guard: an email change on an ACTIVE user must not reach into invite rows. That account
     * can hold a redeemable admin-issued reset link (S-7), so this is not "there is nothing to
     * hijack" -- the takeover it would enable is refused at redemption instead, by {@code
     * UserInviteService.accept} comparing the invite's issued address to the account's (S-10).
     */
    @Test
    void changingActiveUserEmailDoesNotTouchInvites() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("kenneth@example.com")
              .status(UserStatus.ACTIVE)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));
      when(appUserRepository.findByEmail("kenneth@example.com")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"kenneth@example.com\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).updateEmail(8L, "kenneth@example.com");
      verify(userInviteService, never()).invalidateInvites(anyLong());
    }

    /**
     * A no-op email change -- same address, re-cased -- must not kill the outstanding invite: the
     * link is still bound to the same address, so it stays live. The frontend always sends the
     * email field, so this re-cased-but-unchanged path is common. The email write still fires as an
     * idempotent same-value write; only the invite is left alone.
     */
    @Test
    void resubmittingInvitedUserSameEmailDoesNotInvalidateInvite() throws Exception {
      AppUserEntity ken =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L)).thenReturn(Optional.of(ken));
      when(appUserRepository.findByEmail("ken@example.com")).thenReturn(Optional.of(ken));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"KEN@EXAMPLE.COM\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk());

      verify(userInviteService, never()).invalidateInvites(anyLong());
    }

    /**
     * S-9: an invite outlives the account being disabled by up to its 7-day TTL, so the token must
     * die with the transition. Mutation this catches: drop the invalidate call and a disabled user
     * keeps a redeemable link.
     */
    @Test
    void disablingUserInvalidatesOutstandingInvites() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.DISABLED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"DISABLED\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository).updateStatus(8L, UserStatus.DISABLED);
      verify(userInviteService).invalidateInvitesForStatus(8L, UserStatus.DISABLED);
    }

    /**
     * The sweep keys off the resulting status, not off a transition, so an invite issued while the
     * account was already DISABLED is still killed. Mutation this catches: rewrite the condition as
     * a transition test ({@code before != DISABLED && after == DISABLED}) and this goes red.
     */
    @Test
    void reDisablingAlreadyDisabledUserStillSweepsInvites() throws Exception {
      AppUserEntity dan =
          AppUserEntity.builder()
              .id(9L)
              .email("dan@example.com")
              .status(UserStatus.DISABLED)
              .build();
      when(appUserRepository.findById(9L)).thenReturn(Optional.of(dan));

      mockMvc
          .perform(
              patch("/api/admin/users/9")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Dan\",\"status\":\"DISABLED\"}"))
          .andExpect(status().isOk());

      verify(userInviteService).invalidateInvitesForStatus(9L, UserStatus.DISABLED);
    }

    /**
     * The scope guard on the other side: restoring an account must leave its admin-issued
     * password-reset link alone. Mutation this catches: invalidate on every status write, and an
     * admin who re-enables a user silently breaks the reset link they just sent.
     */
    @Test
    void reEnablingUserToActiveDoesNotInvalidateInvites() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.DISABLED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk());

      verify(userInviteService).invalidateInvitesForStatus(8L, UserStatus.ACTIVE);
    }

    /**
     * S-8: the sessions a disabled account already holds keep resolving until each one's next
     * request reads the new status. Mutation this catches: drop the revoke call and the handler
     * leaves live {@code user_session} rows behind.
     */
    @Test
    void disablingUserRevokesLiveSessions() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.DISABLED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"DISABLED\"}"))
          .andExpect(status().isOk());

      verify(sessionService).revokeAllForStatus(8L, UserStatus.DISABLED);
    }

    /**
     * The two sweeps run off different allowlists, and INVITED is the status that separates them:
     * an INVITED account may hold a live invite, but may not hold a working session.
     *
     * <p>What this pins is that the handler delegates both sweeps with the resulting status, and
     * nothing beyond that. It cannot police the predicates behind them: {@code sessionService} is a
     * mock here and {@code mayHoldSession} is static, so the sweep's own guard never runs. An
     * inline comment in this method used to claim it caught the mutation that keys the session
     * sweep off {@code mayAcceptInvite}. It does not -- under that mutation this class stays green
     * at 54 cases.
     *
     * <p>The tests that do catch those mutations both live in {@code
     * SessionServiceIntegrationTest}: keying the sweep off {@code mayAcceptInvite} reddens {@code
     * revokeAllForStatusRevokesOnDemotionToInvited}, and widening {@code mayHoldSession} itself
     * reddens {@code mayHoldSessionAdmitsActiveAndNothingElse}, the literal pin over the whole
     * {@code UserStatus} enum.
     */
    @Test
    void demotingUserToInvitedRevokesSessionsButKeepsInvites() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk());

      verify(sessionService).revokeAllForStatus(8L, UserStatus.INVITED);
      verify(userInviteService).invalidateInvitesForStatus(8L, UserStatus.INVITED);
    }

    /**
     * The scope guard: the handler always delegates, and the ACTIVE no-op is decided inside {@code
     * SessionService} (proved in {@code SessionServiceIntegrationTest}). What this pins is that the
     * controller passes the resulting status through rather than branching on it -- working rule
     * 19. Mutation this catches: wrap the call in an {@code if (status == DISABLED)}.
     */
    @Test
    void reEnablingUserToActiveDoesNotRevokeSessions() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .status(UserStatus.DISABLED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder().id(8L).email("ken@example.com").status(UserStatus.ACTIVE).build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Ken\",\"status\":\"ACTIVE\"}"))
          .andExpect(status().isOk());

      verify(sessionService).revokeAllForStatus(8L, UserStatus.ACTIVE);
    }

    /**
     * An email-less PATCH -- status and name only -- never touches the email, so an INVITED user's
     * outstanding invite must survive: nothing about the login identity changed.
     */
    @Test
    void emaillessPatchOnInvitedUserDoesNotInvalidateInvite() throws Exception {
      AppUserEntity before =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Ken")
              .status(UserStatus.INVITED)
              .build();
      AppUserEntity after =
          AppUserEntity.builder()
              .id(8L)
              .email("ken@example.com")
              .name("Kenneth")
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(8L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));

      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Kenneth\",\"status\":\"INVITED\"}"))
          .andExpect(status().isOk());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(userInviteService, never()).invalidateInvites(anyLong());
    }
  }

  @Nested
  class RoleMembership {

    @Test
    void userRolesReturnsMemberships() throws Exception {
      when(roleRepository.rolesForUser(5L))
          .thenReturn(List.of(RoleEntity.builder().id(2L).name("edens family").build()));

      mockMvc
          .perform(get("/api/admin/users/5/roles"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].name").value("edens family"));
    }

    @Test
    void addUserToRoleReturns204() throws Exception {
      mockMvc.perform(put("/api/admin/users/5/roles/2")).andExpect(status().isNoContent());

      verify(roleRepository).addMember(2L, 5L, null);
    }

    @Test
    void removeUserFromRoleReturns204() throws Exception {
      mockMvc.perform(delete("/api/admin/users/5/roles/2")).andExpect(status().isNoContent());

      verify(roleRepository).removeMember(2L, 5L);
    }

    /**
     * The repository's PERSON rejection must still reach the caller as a {@code 400} on the
     * user-centric route, which reaches it only by delegating to {@code AdminRoleController}.
     */
    @Test
    void addUserToRoleReturns400WhenTargetIsNotAnAccount() throws Exception {
      doThrow(new IllegalArgumentException("Role membership requires an account user: 5"))
          .when(roleRepository)
          .addMember(2L, 5L, null);

      mockMvc.perform(put("/api/admin/users/5/roles/2")).andExpect(status().isBadRequest());
    }

    @Test
    void getUserPageReturns200WithCollectionModel() throws Exception {
      CollectionModel page =
          CollectionModel.builder()
              .slug("user")
              .title("Alice")
              .visibility(CollectionVisibility.UNLISTED)
              .content(List.of())
              .contentCount(0)
              .contentPerPage(0)
              .currentPage(0)
              .totalPages(1)
              .build();
      when(userPageAssembler.assembleForUser(10L)).thenReturn(page);

      mockMvc
          .perform(get("/api/admin/users/10/page"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.slug", is("user")))
          .andExpect(jsonPath("$.title", is("Alice")));
    }
  }

  /**
   * The by-id counterparts of the self-serve space reads, so an admin can render another user's
   * space. Each delegates straight to its service with the path id — never the acting admin's own
   * id, which is the bug these tests pin.
   */
  @Nested
  class UserSpaceReads {

    @Test
    void getUserSavedImagesReturns200WithImageModels() throws Exception {
      when(userSavesService.listSavedImages(10L))
          .thenReturn(
              List.of(
                  imageModel(42L, "Newer", "https://cdn.example.com/newer.jpg"),
                  imageModel(43L, "Older", "https://cdn.example.com/older.jpg")));

      mockMvc
          .perform(get("/api/admin/users/10/saves/images"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(42))
          .andExpect(jsonPath("$[0].title", is("Newer")))
          .andExpect(jsonPath("$[0].imageUrl", is("https://cdn.example.com/newer.jpg")))
          .andExpect(jsonPath("$[1].id").value(43));

      verify(userSavesService).listSavedImages(10L);
    }

    @Test
    void getUserSavedImagesReturnsEmptyArrayWhenNoneSaved() throws Exception {
      when(userSavesService.listSavedImages(10L)).thenReturn(List.of());

      mockMvc
          .perform(get("/api/admin/users/10/saves/images"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getUserFollowsReturns200WithCollectionIds() throws Exception {
      when(userFollowsService.listFollowedCollectionIds(10L)).thenReturn(List.of(7L, 9L));

      mockMvc
          .perform(get("/api/admin/users/10/follows"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0]").value(7))
          .andExpect(jsonPath("$[1]").value(9));

      verify(userFollowsService).listFollowedCollectionIds(10L);
    }

    @Test
    void getUserFollowsReturnsEmptyArrayWhenNoneFollowed() throws Exception {
      when(userFollowsService.listFollowedCollectionIds(10L)).thenReturn(List.of());

      mockMvc
          .perform(get("/api/admin/users/10/follows"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    private ContentModels.Image imageModel(Long id, String title, String imageUrl) {
      return new ContentModels.Image(
          id,
          ContentType.IMAGE,
          title,
          null,
          null,
          null,
          imageUrl,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          null,
          List.of(),
          List.of(),
          List.of());
    }
  }

  @Nested
  class Passkeys {

    private WebAuthnCredentialEntity credential(Long id, String label) {
      return WebAuthnCredentialEntity.builder()
          .id(id)
          .userId(7L)
          .credentialId(new byte[] {1, 2, 3})
          .publicKey(new byte[] {4, 5, 6})
          .signCount(0L)
          .transports("internal")
          .label(label)
          .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
          .build();
    }

    @Test
    void listPasskeysReturnsMetadataWithoutKeyMaterial() throws Exception {
      when(credentialRepository.findByUserId(7L))
          .thenReturn(List.of(credential(11L, "YubiKey"), credential(12L, "iPhone")));

      mockMvc
          .perform(get("/api/admin/users/7/passkeys"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(11))
          .andExpect(jsonPath("$[0].label").value("YubiKey"))
          .andExpect(jsonPath("$[0].publicKey").doesNotExist())
          .andExpect(jsonPath("$[0].credentialId").doesNotExist());
    }

    @Test
    void deregisterPasskeyReportsWhatIsLeft() throws Exception {
      when(credentialRepository.deleteByIdAndUserId(11L, 7L)).thenReturn(1);
      when(credentialRepository.findByUserId(7L)).thenReturn(List.of(credential(12L, "iPhone")));
      when(appUserRepository.findById(7L))
          .thenReturn(
              Optional.of(AppUserEntity.builder().id(7L).passwordHash("{bcrypt}x").build()));

      mockMvc
          .perform(delete("/api/admin/users/7/passkeys/11"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.remainingPasskeys").value(1))
          .andExpect(jsonPath("$.passwordLoginAvailable").value(true));
    }

    @Test
    void deregisterLastPasskeyIsAllowedAndReportsTheAccountCannotLogIn() throws Exception {
      when(credentialRepository.deleteByIdAndUserId(11L, 7L)).thenReturn(1);
      when(credentialRepository.findByUserId(7L)).thenReturn(List.of());
      when(appUserRepository.findById(7L))
          .thenReturn(Optional.of(AppUserEntity.builder().id(7L).passwordHash(null).build()));

      mockMvc
          .perform(delete("/api/admin/users/7/passkeys/11"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.remainingPasskeys").value(0))
          .andExpect(jsonPath("$.passwordLoginAvailable").value(false));

      verify(credentialRepository).deleteByIdAndUserId(11L, 7L);
    }

    @Test
    void deregisterPasskeyBelongingToAnotherUserIs404() throws Exception {
      when(credentialRepository.deleteByIdAndUserId(11L, 7L)).thenReturn(0);

      mockMvc.perform(delete("/api/admin/users/7/passkeys/11")).andExpect(status().isNotFound());

      verify(appUserRepository, never()).findById(7L);
    }

    /**
     * S-26: a successful deregistration revokes the account's live sessions, or the deleted
     * authenticator's session keeps resolving and can register a replacement passkey. Mutation this
     * catches: drop the {@code revokeAllForUser} call from {@code deregisterPasskey}.
     */
    @Test
    void deregisterPasskeyRevokesTheAccountsLiveSessions() throws Exception {
      when(credentialRepository.deleteByIdAndUserId(11L, 7L)).thenReturn(1);
      when(credentialRepository.findByUserId(7L)).thenReturn(List.of());
      when(appUserRepository.findById(7L))
          .thenReturn(Optional.of(AppUserEntity.builder().id(7L).passwordHash(null).build()));

      mockMvc.perform(delete("/api/admin/users/7/passkeys/11")).andExpect(status().isOk());

      verify(sessionService).revokeAllForUser(7L);
    }

    /**
     * The scope guard on the other side: the revoke sits below the delete guard, so a credential
     * the account does not own is a 404 that touches nothing. Mutation this catches: hoist the
     * {@code revokeAllForUser} call above the {@code deleteByIdAndUserId} check and any admin
     * guessing a credential id logs the account out.
     */
    @Test
    void deregisterPasskeyThatIs404RevokesNothing() throws Exception {
      when(credentialRepository.deleteByIdAndUserId(11L, 7L)).thenReturn(0);

      mockMvc.perform(delete("/api/admin/users/7/passkeys/11")).andExpect(status().isNotFound());

      verify(sessionService, never()).revokeAllForUser(anyLong());
    }
  }
}
