package edens.zac.portfolio.backend.controller.admin;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.UserFollowsService;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.services.UserMergeService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.services.UserSavesService;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.UserStatus;
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

  // Trailing slash on purpose: exercises the trailing-slash-safe invite-URL join.
  private static final String FRONTEND_BASE_URL = "https://app.example.com/";

  @BeforeEach
  void setUp() {
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
            FRONTEND_BASE_URL);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class CreateUser {

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
          // Trailing slash on the base URL must not produce a double slash before "invite".
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/raw-token-abc"));

      // The invitee is emailed the same link that is returned for copy-linking.
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

      // verify findByEmail was called with lowercase email
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
          // Sensitive fields must never be serialized into the admin list.
          .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
          .andExpect(jsonPath("$[0].webauthnUserHandle").doesNotExist())
          .andExpect(jsonPath("$[1].status").value("INVITED"));
    }

    @Test
    void listUsersExcludesPersonTagRows() throws Exception {
      // V35 merged content_people into users as status=PERSON rows (tag-only identities, no
      // account). The admin account list must skip them -- and must not 400 on the new enum value.
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

      // A re-issued invite is emailed too, so a resend reaches the invitee without a manual copy.
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

    @Test
    void upgradeEmailsTheInviteToTheUpgradedPerson() throws Exception {
      // Upgrade is an invite-minting endpoint like create-user and regenerate-invite, so it must
      // deliver the link too -- the person's existing tag name is the greeting name.
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

      // Both the duplicate check and the write must see the lowercased email.
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

    @Test
    void upgradeNonPersonUserReturns409() throws Exception {
      // Only a tag-only PERSON can be upgraded: an ACTIVE/INVITED/DISABLED row is already an
      // account, so upgrading it (clobbering its email, resetting its status) is a conflict.
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

      // 409 is a normal return, so @Transactional commits this path: NO field may have been
      // written by the time the conflict is detected, or a partial upgrade would persist.
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

      // The person's tagged name is preserved as the account display name — never overwritten.
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
      // First findById gates the 404 check; second reads the refreshed row back.
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

      // 409 is a normal return, so @Transactional commits this path: NO field may have been
      // written by the time the conflict is detected, or a partial update would persist.
      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
      verify(appUserRepository, never()).updateName(anyLong(), any());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(appUserRepository, never()).updateDescription(anyLong(), any());
    }

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

      // Both the duplicate check and the write must see the lowercased email.
      verify(appUserRepository).findByEmail("kenneth@example.com");
      verify(appUserRepository).updateEmail(8L, "kenneth@example.com");
    }

    @Test
    void resubmittingOwnEmailWithDifferentCaseReturns200NotConflict() throws Exception {
      // The frontend always sends the email field, so "unchanged email" (possibly re-cased) is
      // the common path — the duplicate check must not trip on the user's own row.
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

    @Test
    void updateWithMalformedEmailReturns400() throws Exception {
      // Pins the @Email constraint on UpdateUserRequest: bean validation rejects the body
      // before the controller body runs, so no findById stub is needed and nothing is written.
      mockMvc
          .perform(
              patch("/api/admin/users/8")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\",\"status\":\"INVITED\"}"))
          .andExpect(status().isBadRequest());

      verify(appUserRepository, never()).updateEmail(anyLong(), anyString());
    }

    @Test
    void changingInvitedUserEmailInvalidatesOutstandingInvite() throws Exception {
      // Account-takeover guard: an INVITED user has an outstanding invite bound to their OLD
      // address. When the admin corrects the email, the old link must die so whoever holds it
      // (e.g. the prior address's inbox) can no longer redeem it onto the corrected account.
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

    @Test
    void changingActiveUserEmailDoesNotTouchInvites() throws Exception {
      // Scope guard: an ACTIVE user has no pending onboarding invite to hijack, so an email
      // change must NOT reach into invite rows (an ACTIVE user's stray unused invite, if one
      // even exists, is not the account-takeover concern here).
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

    @Test
    void resubmittingInvitedUserSameEmailDoesNotInvalidateInvite() throws Exception {
      // No-op email change (same address, re-cased) must NOT kill the outstanding invite: the
      // link is still bound to the same address, so it stays live. The frontend always sends the
      // email field, so this re-cased-but-unchanged path is common.
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

      // The email write still fires (idempotent same-value write), but the invite is left alone.
      verify(userInviteService, never()).invalidateInvites(anyLong());
    }

    @Test
    void disablingUserInvalidatesOutstandingInvites() throws Exception {
      // S-9: an invite outlives the account being disabled by up to its 7-day TTL, so the token
      // must die with the transition. Mutation this catches: drop the invalidate call and a
      // disabled user keeps a redeemable link.
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

    @Test
    void reDisablingAlreadyDisabledUserStillSweepsInvites() throws Exception {
      // The sweep keys off the resulting status, not off a transition, so an invite issued while
      // the account was already DISABLED is still killed. Mutation this catches: rewrite the
      // condition as a transition test (before != DISABLED && after == DISABLED) and this goes red.
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

    @Test
    void reEnablingUserToActiveDoesNotInvalidateInvites() throws Exception {
      // The scope guard on the other side: restoring an account must leave its admin-issued
      // password-reset link alone. Mutation this catches: invalidate on every status write and
      // an admin who re-enables a user silently breaks the reset link they just sent.
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

    @Test
    void disablingUserRevokesLiveSessions() throws Exception {
      // S-8: the sessions a disabled account already holds keep resolving until each one's next
      // request reads the new status. Mutation this catches: drop the revoke call and the handler
      // leaves live user_session rows behind.
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

    @Test
    void demotingUserToInvitedRevokesSessionsButKeepsInvites() throws Exception {
      // The two sweeps run off different allowlists, and INVITED is the status that separates
      // them: an INVITED account may hold a live invite, but may not hold a working session.
      // Mutation this catches: key the session sweep off mayAcceptInvite and this goes red.
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

    @Test
    void reEnablingUserToActiveDoesNotRevokeSessions() throws Exception {
      // The scope guard: the handler always delegates, and the ACTIVE no-op is decided inside
      // SessionService (proved in SessionServiceIntegrationTest). What this pins is that the
      // controller passes the resulting status through rather than branching on it -- working
      // rule 19. Mutation this catches: wrap the call in an `if (status == DISABLED)`.
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

    @Test
    void emaillessPatchOnInvitedUserDoesNotInvalidateInvite() throws Exception {
      // An email-less PATCH (status/name only) never touches the email, so an INVITED user's
      // outstanding invite must survive — nothing about the login identity changed.
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

      // The path id is what reaches the service — not the acting admin's id.
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
}
