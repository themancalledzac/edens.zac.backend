package edens.zac.portfolio.backend.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.AuthLoginLimiter;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.LoginRequest;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private SessionService sessionService;
  @Mock private AuthLoginLimiter loginLimiter;
  @Mock private AppUserRepository appUserRepository;
  @Mock private UserCollectionRepository userCollectionRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private AuthController authController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private AppUserEntity admin() {
    return AppUserEntity.builder()
        .id(1L)
        .email("admin@example.com")
        .passwordHash("{bcrypt}$2a$10$hash")
        .webauthnUserHandle(UUID.randomUUID())
        .status(UserStatus.ACTIVE)
        .build();
  }

  @Test
  void loginWithValidCredentialsReturns204AndCreatesSession() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
    when(passwordEncoder.matches("correct", "{bcrypt}$2a$10$hash")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@example.com", "correct"))))
        .andExpect(status().isNoContent());

    verify(loginLimiter).reset(anyString(), eq("admin@example.com"));
    verify(sessionService).create(any(AppUserEntity.class), eq(false), any(), any());
  }

  @Test
  void loginWithMixedCaseEmailResolvesLowercasedUser() throws Exception {
    // Email stored lowercased at creation time; a mixed-case login must still resolve it.
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
    when(passwordEncoder.matches("correct", "{bcrypt}$2a$10$hash")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("ADMIN@Example.COM", "correct"))))
        .andExpect(status().isNoContent());

    verify(appUserRepository).findByEmail("admin@example.com");
    verify(sessionService).create(any(AppUserEntity.class), eq(false), any(), any());
  }

  @Test
  void loginWithBadPasswordReturns401AndRecordsFailure() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
    when(passwordEncoder.matches("wrong", "{bcrypt}$2a$10$hash")).thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@example.com", "wrong"))))
        .andExpect(status().isUnauthorized());

    verify(loginLimiter).recordFailure(anyString(), eq("admin@example.com"));
    verify(sessionService, never()).create(any(), anyBooleanWrapper(), any(), any());
  }

  // Helper to keep the never()-verify readable; Mockito's eq for boolean is fine inline too.
  private static boolean anyBooleanWrapper() {
    return org.mockito.ArgumentMatchers.anyBoolean();
  }

  @Test
  void loginForUnknownEmailReturns401Generic() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("ghost@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest("ghost@example.com", "x"))))
        .andExpect(status().isUnauthorized());

    verify(loginLimiter).recordFailure(anyString(), eq("ghost@example.com"));
    // Dummy BCrypt check must be performed to equalize timing with the wrong-password branch.
    verify(passwordEncoder).matches(eq("x"), anyString());
  }

  @Test
  void unknownEmailAndWrongPasswordBothReturn401WithNoBody() throws Exception {
    // Unknown email branch
    when(loginLimiter.isBlocked(anyString(), eq("ghost@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest("ghost@example.com", "x"))))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    result.getResponse().getContentAsString().isEmpty(),
                    "401 body must be empty for unknown email"));

    // Wrong password branch
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
    when(passwordEncoder.matches("wrong", "{bcrypt}$2a$10$hash")).thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@example.com", "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    result.getResponse().getContentAsString().isEmpty(),
                    "401 body must be empty for wrong password"));
  }

  @Test
  void loginWhenRateLimitedReturns429() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest("admin@example.com", "x"))))
        .andExpect(status().isTooManyRequests());

    verify(appUserRepository, never()).findByEmail(anyString());
  }

  @Test
  void logoutReturns204AndRevokes() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("ezac_session", "tok")))
        .andExpect(status().isNoContent());

    verify(sessionService).revoke(eq("tok"), any());
  }

  @Test
  void meReturns200WithPrincipal() throws Exception {
    AuthPrincipal principal = new AuthPrincipal(1L, "admin@example.com", false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    when(userCollectionRepository.findByUserId(1L)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", org.hamcrest.Matchers.is("admin@example.com")))
        .andExpect(jsonPath("$.mfaSatisfied", org.hamcrest.Matchers.is(false)))
        .andExpect(jsonPath("$.galleries", org.hamcrest.Matchers.hasSize(0)));
  }

  @Test
  void meReturns401WhenAnonymous() throws Exception {
    // No authentication in the context.
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
