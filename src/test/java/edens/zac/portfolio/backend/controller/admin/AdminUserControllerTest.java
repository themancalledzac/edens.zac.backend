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
import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import edens.zac.portfolio.backend.dao.UserCollectionRepository.AssociatedCollection;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.services.UserMergeService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.types.CollectionRole;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
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
  @Mock private UserCollectionRepository userCollectionRepository;
  @Mock private UserPageAssembler userPageAssembler;
  @Mock private UserMergeService userMergeService;

  // Trailing slash on purpose: exercises the trailing-slash-safe invite-URL join.
  private static final String FRONTEND_BASE_URL = "https://app.example.com/";

  @BeforeEach
  void setUp() {
    AdminUserController controller =
        new AdminUserController(
            appUserRepository,
            userInviteService,
            userCollectionRepository,
            userPageAssembler,
            userMergeService,
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
              .status(UserStatus.INVITED)
              .build();
      when(appUserRepository.findById(5L)).thenReturn(Optional.of(bob));
      when(userInviteService.regenerateInvite(5L, "bob@example.com")).thenReturn("fresh-token");

      mockMvc
          .perform(post("/api/admin/users/5/invite"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(5))
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/fresh-token"));
    }

    @Test
    void regenerateUnknownUserReturns404() throws Exception {
      when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

      mockMvc.perform(post("/api/admin/users/999/invite")).andExpect(status().isNotFound());

      verify(userInviteService, never()).regenerateInvite(anyLong(), anyString());
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
  class CollectionMembership {

    @Test
    void putCollectionRoleReturns204AndCallsUpsert() throws Exception {
      mockMvc
          .perform(
              put("/api/admin/users/10/collections/20")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"role\":\"CLIENT\"}"))
          .andExpect(status().isNoContent());

      verify(userCollectionRepository).upsertRole(10L, 20L, CollectionRole.CLIENT, null);
    }

    @Test
    void deleteCollectionRoleReturns204AndCallsDelete() throws Exception {
      mockMvc
          .perform(delete("/api/admin/users/10/collections/20"))
          .andExpect(status().isNoContent());

      verify(userCollectionRepository).delete(10L, 20L);
    }

    @Test
    void getCollectionsReturnsAssociatedRowsWithNullRoleBeforeGrant() throws Exception {
      when(userCollectionRepository.findAssociatedCollections(10L))
          .thenReturn(List.of(new AssociatedCollection(42L, "Wedding Gallery", null)));

      mockMvc
          .perform(get("/api/admin/users/10/collections"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].collectionId", is(42)))
          .andExpect(jsonPath("$[0].title", is("Wedding Gallery")))
          .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void getUserPageReturns200WithCollectionModel() throws Exception {
      CollectionModel page =
          CollectionModel.builder()
              .slug("user")
              .title("Alice")
              .type(CollectionType.PARENT)
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
          .andExpect(jsonPath("$.title", is("Alice")))
          .andExpect(jsonPath("$.type", is("PARENT")));
    }

    @Test
    void putCollectionRoleReturns400OnMissingRole() throws Exception {
      mockMvc
          .perform(
              put("/api/admin/users/10/collections/20")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void putCollectionRoleReturns400OnUnparseableRoleEnum() throws Exception {
      // Unknown enum value -> Jackson cannot deserialize the body, so this is an
      // HttpMessageNotReadableException (a malformed body), NOT bean validation. It previously
      // fell through to the catch-all Exception handler and returned 500 instead of 400.
      mockMvc
          .perform(
              put("/api/admin/users/10/collections/20")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"role\":\"INVALID_ROLE\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
