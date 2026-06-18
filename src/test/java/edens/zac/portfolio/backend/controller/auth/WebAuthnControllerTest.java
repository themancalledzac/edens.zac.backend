package edens.zac.portfolio.backend.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.AuthLoginLimiter;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.WebAuthnService;
import edens.zac.portfolio.backend.types.Role;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc tests for {@link WebAuthnController} with a mocked {@link WebAuthnService}
 * (the ceremony engine itself is unit-tested in WebAuthnServiceTest). Asserts the four endpoints,
 * the attempt-cookie transport on login, and the 401 when the attempt cookie is absent.
 */
class WebAuthnControllerTest {

  private WebAuthnService webAuthnService;
  private AuthLoginLimiter loginLimiter;
  private MockMvc mockMvc;

  private final AuthPrincipal admin = new AuthPrincipal(1L, "admin@example.com", Role.ADMIN, false);

  @BeforeEach
  void setUp() {
    webAuthnService = mock(WebAuthnService.class);
    loginLimiter = mock(AuthLoginLimiter.class);
    ObjectMapper webAuthnObjectMapper = new ObjectMapper();
    webAuthnObjectMapper.registerModule(new WebauthnJackson2Module());
    WebAuthnController controller =
        new WebAuthnController(webAuthnService, webAuthnObjectMapper, loginLimiter, false);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor asAdmin() {
    return request -> {
      var auth =
          new UsernamePasswordAuthenticationToken(
              admin, null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
      SecurityContextHolder.getContext().setAuthentication(auth);
      return request;
    };
  }

  @Test
  void registerStartReturns200WithOptions() throws Exception {
    when(webAuthnService.startRegistration(any())).thenReturn(creationOptions());

    mockMvc
        .perform(post("/api/auth/webauthn/register/start").with(asAdmin()))
        .andExpect(status().isOk());
  }

  @Test
  void registerFinishReturns204AndDelegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/webauthn/register/finish")
                .with(asAdmin())
                .contentType("application/json")
                .content("{\"id\":\"abc\"}"))
        .andExpect(status().isNoContent());

    verify(webAuthnService).finishRegistration(any(), eq("{\"id\":\"abc\"}"));
  }

  @Test
  void loginStartReturns200WithOptionsAndSetsAttemptCookie() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(false);
    when(webAuthnService.startLogin(eq("admin@example.com")))
        .thenReturn(new WebAuthnService.LoginStart("attempt-1", requestOptions()));

    mockMvc
        .perform(
            post("/api/auth/webauthn/login/start")
                .contentType("application/json")
                .content("{\"email\":\"admin@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("ezac_webauthn_attempt"))
        .andExpect(cookie().value("ezac_webauthn_attempt", "attempt-1"))
        .andExpect(cookie().httpOnly("ezac_webauthn_attempt", true));

    verify(loginLimiter).recordFailure(anyString(), eq("admin@example.com"));
  }

  @Test
  void loginStartReturns429WhenRateLimited() throws Exception {
    when(loginLimiter.isBlocked(anyString(), eq("admin@example.com"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/webauthn/login/start")
                .contentType("application/json")
                .content("{\"email\":\"admin@example.com\"}"))
        .andExpect(status().isTooManyRequests());

    verify(webAuthnService, never()).startLogin(any());
  }

  @Test
  void loginFinishReadsAttemptCookieDelegatesAndClearsCookie() throws Exception {
    when(webAuthnService.finishLogin(eq("attempt-1"), eq("{\"id\":\"abc\"}"), any(), any()))
        .thenReturn("admin@example.com");

    mockMvc
        .perform(
            post("/api/auth/webauthn/login/finish")
                .cookie(new jakarta.servlet.http.Cookie("ezac_webauthn_attempt", "attempt-1"))
                .contentType("application/json")
                .content("{\"id\":\"abc\"}"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("ezac_webauthn_attempt", 0));

    verify(webAuthnService).finishLogin(eq("attempt-1"), eq("{\"id\":\"abc\"}"), any(), any());
    verify(loginLimiter).reset(anyString(), eq("admin@example.com"));
  }

  @Test
  void loginFinishClearsCookieEvenOnServiceException() throws Exception {
    when(webAuthnService.finishLogin(eq("attempt-1"), any(), any(), any()))
        .thenThrow(new IllegalStateException("bad assertion"));

    // MockMvc standalone rethrows unhandled controller exceptions. Add a catch-all exception
    // resolver so the response object is populated (and the Set-Cookie from the finally block is
    // readable) without the exception blowing up the perform() call.
    WebAuthnController controller =
        new WebAuthnController(webAuthnService, new ObjectMapper(), loginLimiter, false);
    MockMvc exceptionHandlingMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setHandlerExceptionResolvers(
                (request, response, handler, ex) -> {
                  response.setStatus(500);
                  return new org.springframework.web.servlet.ModelAndView();
                })
            .build();

    org.springframework.mock.web.MockHttpServletResponse response =
        exceptionHandlingMvc
            .perform(
                post("/api/auth/webauthn/login/finish")
                    .cookie(new jakarta.servlet.http.Cookie("ezac_webauthn_attempt", "attempt-1"))
                    .contentType("application/json")
                    .content("{\"id\":\"abc\"}"))
            .andReturn()
            .getResponse();

    boolean clearedCookiePresent =
        response.getHeaders(org.springframework.http.HttpHeaders.SET_COOKIE).stream()
            .anyMatch(h -> h.contains("ezac_webauthn_attempt") && h.contains("Max-Age=0"));
    org.junit.jupiter.api.Assertions.assertTrue(
        clearedCookiePresent,
        "attempt cookie must be cleared (Max-Age=0) even when finishLogin throws");
  }

  @Test
  void loginFinishWithoutAttemptCookieReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/webauthn/login/finish")
                .contentType("application/json")
                .content("{\"id\":\"abc\"}"))
        .andExpect(status().isUnauthorized());

    verify(webAuthnService, never()).finishLogin(any(), any(), any(), any());
  }

  /** A real (minimal) creation-options document the WebAuthn Jackson module can serialize. */
  private static PublicKeyCredentialCreationOptions creationOptions() {
    return PublicKeyCredentialCreationOptions.builder()
        .rp(PublicKeyCredentialRpEntity.builder().id("localhost").name("Test RP").build())
        .user(
            ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name("admin@example.com")
                .displayName("admin@example.com")
                .build())
        .challenge(Bytes.random())
        .pubKeyCredParams(List.of(PublicKeyCredentialParameters.ES256))
        .timeout(Duration.ofMinutes(5))
        .build();
  }

  /** A real (minimal) request-options document the WebAuthn Jackson module can serialize. */
  private static PublicKeyCredentialRequestOptions requestOptions() {
    return PublicKeyCredentialRequestOptions.builder()
        .challenge(Bytes.random())
        .rpId("localhost")
        .timeout(Duration.ofMinutes(5))
        .build();
  }
}
