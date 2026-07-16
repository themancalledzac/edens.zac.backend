package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.RoleCollectionGrant;
import edens.zac.portfolio.backend.dao.RoleRepository.RoleMember;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.RoleKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminRoleControllerTest {

  private RoleRepository roleRepository;
  private MockMvc mvc;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setup() {
    roleRepository = Mockito.mock(RoleRepository.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new AdminRoleController(roleRepository))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listRolesReturnsSummaries() throws Exception {
    when(roleRepository.findAll())
        .thenReturn(
            List.of(
                RoleEntity.builder().id(1L).name("edens family").kind(RoleKind.SHARED).build()));
    mvc.perform(get("/api/admin/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("edens family"));
  }

  @Test
  void createRoleReturns201WithId() throws Exception {
    when(roleRepository.createRole(eq("the bois"), eq(RoleKind.SHARED), any())).thenReturn(7L);
    mvc.perform(
            post("/api/admin/roles")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(new RoleRequests.CreateRoleRequest("the bois", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(7));
  }

  @Test
  void getRoleReturnsDetailWithMembersAndCollections() throws Exception {
    when(roleRepository.findById(7L))
        .thenReturn(
            Optional.of(
                RoleEntity.builder().id(7L).name("edens family").kind(RoleKind.SHARED).build()));
    when(roleRepository.membersForRole(7L))
        .thenReturn(List.of(new RoleMember(3L, "mara@example.com", "Mara")));
    when(roleRepository.grantsForRole(7L))
        .thenReturn(List.of(new RoleCollectionGrant(9L, "Wedding Gallery", AccessLevel.CLIENT)));

    mvc.perform(get("/api/admin/roles/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("edens family"))
        .andExpect(jsonPath("$.members[0].email").value("mara@example.com"))
        .andExpect(jsonPath("$.members[0].name").value("Mara"))
        .andExpect(jsonPath("$.collections[0].collectionId").value(9))
        .andExpect(jsonPath("$.collections[0].level").value("CLIENT"));
  }

  @Test
  void getRoleReturns404WhenMissing() throws Exception {
    when(roleRepository.findById(404L)).thenReturn(Optional.empty());
    mvc.perform(get("/api/admin/roles/404")).andExpect(status().isNotFound());
  }

  @Test
  void setGrantReturns204AndUpserts() throws Exception {
    mvc.perform(
            put("/api/admin/roles/7/collections/9")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        new RoleRequests.SetRoleGrantRequest(AccessLevel.CLIENT))))
        .andExpect(status().isNoContent());
    verify(roleRepository).setCollectionGrant(7L, 9L, AccessLevel.CLIENT, null);
  }

  @Test
  void setGrantReturns400OnUnparseableLevelEnum() throws Exception {
    // Unknown enum value -> Jackson cannot deserialize the body, so this is an
    // HttpMessageNotReadableException (a malformed body) handled by GlobalExceptionHandler as 400.
    mvc.perform(
            put("/api/admin/roles/7/collections/9")
                .contentType("application/json")
                .content("{\"level\":\"INVALID_LEVEL\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeGrantReturns204() throws Exception {
    mvc.perform(delete("/api/admin/roles/7/collections/9")).andExpect(status().isNoContent());
    verify(roleRepository).removeCollectionGrant(7L, 9L);
  }

  @Test
  void addMemberReturns204() throws Exception {
    mvc.perform(put("/api/admin/roles/7/members/3")).andExpect(status().isNoContent());
    verify(roleRepository).addMember(7L, 3L, null);
  }

  @Test
  void removeMemberReturns204() throws Exception {
    mvc.perform(delete("/api/admin/roles/7/members/3")).andExpect(status().isNoContent());
    verify(roleRepository).removeMember(7L, 3L);
  }

  @Test
  void deleteRoleReturns204() throws Exception {
    mvc.perform(delete("/api/admin/roles/7")).andExpect(status().isNoContent());
    verify(roleRepository).deleteRole(7L);
  }
}
