package edens.zac.portfolio.backend.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.LoginRequest;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
  @Mock private CollectionAccessService collectionAccessService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private WebAuthnCredentialRepository webAuthnCredentialRepository;

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

  /**
   * Under tr-TR, a locale-sensitive {@code toLowerCase()} maps {@code I} to a dotless lowercase i,
   * so an uppercase email would no longer match the stored lowercase address or the {@link
   * AuthLoginLimiter} key. {@link Locale#ROOT} in the login path keeps the dotted i so both stay in
   * sync.
   */
  @Test
  void login_underTurkishDefaultLocale_stillResolvesLowercasedUser() throws Exception {
    Locale previous = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try {
      when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
      when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
      when(passwordEncoder.matches("correct", "{bcrypt}$2a$10$hash")).thenReturn(true);

      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new LoginRequest("ADMIN@EXAMPLE.COM", "correct"))))
          .andExpect(status().isNoContent());

      verify(appUserRepository).findByEmail("admin@example.com");
      verify(loginLimiter).reset(anyString(), eq("admin@example.com"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  /** Email is stored lowercased at creation time; a mixed-case login must still resolve it. */
  @Test
  void loginWithMixedCaseEmailResolvesLowercasedUser() throws Exception {
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

  /**
   * Keeps the {@code never()} verify readable; Mockito's {@code eq} for boolean works inline too.
   */
  private static boolean anyBooleanWrapper() {
    return org.mockito.ArgumentMatchers.anyBoolean();
  }

  /**
   * Every status the login guard must refuse, read off {@link SessionService#mayHoldSession} rather
   * than written out. Deriving the cases from the predicate the guard now calls is what keeps the
   * two in step: a status added to the enum arrives here on its own, and if it is later admitted to
   * {@code mayHoldSession} it leaves here on its own too.
   *
   * @return the statuses under which an account may not hold a session
   */
  private static Stream<UserStatus> statusesThatMayNotHoldSession() {
    return Arrays.stream(UserStatus.values()).filter(s -> !SessionService.mayHoldSession(s));
  }

  /**
   * Login is an allowlist, not a DISABLED denylist. The stub returning true for the real hash is
   * deliberate and lenient: it makes this test red if the status test is removed, since the login
   * would then succeed on a correct password. Asserting the real hash is never consulted pins the
   * guard ahead of the password check, which is what keeps the ineligible branch paying the same
   * dummy-BCrypt cost as unknown-email and so out of the enumeration oracle.
   *
   * <p>The cases come from {@link #statusesThatMayNotHoldSession()} rather than a written-out list,
   * so a fifth {@code UserStatus} is covered here the moment {@link SessionService#mayHoldSession}
   * refuses it. The list this replaced named DISABLED and INVITED and silently omitted PERSON.
   */
  @ParameterizedTest
  @MethodSource("statusesThatMayNotHoldSession")
  void loginForIneligibleAccountReturns401AndCreatesNoSession(UserStatus status) throws Exception {
    AppUserEntity user =
        AppUserEntity.builder()
            .id(1L)
            .email("admin@example.com")
            .passwordHash("{bcrypt}$2a$10$hash")
            .webauthnUserHandle(UUID.randomUUID())
            .status(status)
            .build();
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
    lenient().when(passwordEncoder.matches("correct", "{bcrypt}$2a$10$hash")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@example.com", "correct"))))
        .andExpect(status().isUnauthorized());

    verify(passwordEncoder, never()).matches("correct", "{bcrypt}$2a$10$hash");
    verify(loginLimiter).recordFailure(anyString(), eq("admin@example.com"));
    verify(sessionService, never()).create(any(), anyBooleanWrapper(), any(), any());
  }

  /** The dummy BCrypt check must run to equalize timing with the wrong-password branch. */
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
    verify(passwordEncoder).matches(eq("x"), anyString());
  }

  @Test
  void unknownEmailAndWrongPasswordBothReturn401WithNoBody() throws Exception {
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
    AuthPrincipal principal = new AuthPrincipal(1L, "admin@example.com", true, false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    when(collectionAccessService.effectiveGrants(1L)).thenReturn(List.of());
    when(webAuthnCredentialRepository.findByUserId(1L)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", org.hamcrest.Matchers.is("admin@example.com")))
        .andExpect(jsonPath("$.isAdmin", org.hamcrest.Matchers.is(true)))
        .andExpect(jsonPath("$.mfaSatisfied", org.hamcrest.Matchers.is(false)))
        .andExpect(jsonPath("$.galleries", org.hamcrest.Matchers.hasSize(0)));
  }

  @Test
  void meReportsHowManyPasskeysTheAccountHolds() throws Exception {
    AuthPrincipal principal = new AuthPrincipal(1L, "admin@example.com", true, false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    when(collectionAccessService.effectiveGrants(1L)).thenReturn(List.of());
    when(webAuthnCredentialRepository.findByUserId(1L))
        .thenReturn(List.of(new WebAuthnCredentialEntity(), new WebAuthnCredentialEntity()));

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passkeyCount").value(2));
  }

  /** No authentication in the security context. */
  @Test
  void meReturns401WhenAnonymous() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
