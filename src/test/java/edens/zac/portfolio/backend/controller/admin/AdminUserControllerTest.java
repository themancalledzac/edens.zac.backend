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
          .andExpect(jsonPath("$[0].status").value("ACTIVE"))
          // Sensitive fields must never be serialized into the admin list.
          .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
          .andExpect(jsonPath("$[0].webauthnUserHandle").doesNotExist())
          .andExpect(jsonPath("$[1].status").value("INVITED"));
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
  }
}
