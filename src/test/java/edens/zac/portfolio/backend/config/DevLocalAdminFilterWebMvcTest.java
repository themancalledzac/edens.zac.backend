package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-profile companion to {@link AdminAuthorizationEnforcedWebMvcTest}. With {@code dev} active
 * and a bootstrap admin configured, an anonymous request reaches {@code /api/admin/**} as that
 * admin; an explicit non-admin session still gets 403, and routes outside the admin surface stay
 * anonymous. Runs the real security chain so the filter's position in it is what is under test.
 */
@WebMvcTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.auth.admin.bootstrap-email=admin@example.com")
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  FlybySessionFilter.class,
  DevLocalAdminFilter.class,
  DevLocalAdminFilterWebMvcTest.StubControllers.class
})
class DevLocalAdminFilterWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;

  @MockBean private ShareLinkService shareLinkService;

  @MockBean private AppUserRepository appUserRepository;

  @Configuration
  static class StubControllers {
    @Bean
    StubController stubController() {
      return new StubController();
    }
  }

  @RestController
  static class StubController {
    @GetMapping("/api/admin/ping")
    String adminGet() {
      return "pong";
    }

    @PostMapping("/api/admin/ping")
    String adminPost() {
      return "pong";
    }

    @GetMapping("/api/auth/me")
    String me() {
      return "me";
    }
  }

  @BeforeEach
  void bootstrapAdminRowExists() {
    when(appUserRepository.findByEmail(eq("admin@example.com")))
        .thenReturn(
            Optional.of(
                AppUserEntity.builder().id(1L).email("admin@example.com").isAdmin(true).build()));
  }

  @Test
  void anonGetAdminIsAllowedAsBootstrapAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isOk());
  }

  @Test
  void anonPostAdminIsAllowedAsBootstrapAdmin() throws Exception {
    mockMvc.perform(post("/api/admin/ping")).andExpect(status().isOk());
  }

  @Test
  void explicitNonAdminSessionIsStillForbidden() throws Exception {
    when(sessionService.resolve(eq("user-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie("ezac_session", "user-token")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shareLinkCookieDoesNotLockTheDevAdminOut() throws Exception {
    when(shareLinkService.resolveByRawToken(eq("flyby-token")))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).build()));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie(FlybyCookies.COOKIE_NAME, "flyby-token")))
        .andExpect(status().isOk());
  }

  @Test
  void routesOutsideAdminSurfaceStayAnonymous() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  private static RequestPostProcessor remoteAddr(String addr) {
    return request -> {
      request.setRemoteAddr(addr);
      return request;
    };
  }

  /** Covers A5: a non-local remote address gets 401 while loopback is still the bootstrap admin. */
  @Test
  void nonLocalRemoteAddressGets401WhileLoopbackIsAdmin() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").with(remoteAddr("192.168.1.50")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/admin/ping").with(remoteAddr("127.0.0.1")))
        .andExpect(status().isOk());
  }

  /** Covers A6: IPv6 loopback is local, other addresses are not. */
  @Test
  void ipv6LoopbackIsLocalAndOtherAddressesAreNot() throws Exception {
    mockMvc.perform(get("/api/admin/ping").with(remoteAddr("::1"))).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/ping").with(remoteAddr("0:0:0:0:0:0:0:1")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/admin/ping").with(remoteAddr("10.0.0.1")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/admin/ping").with(remoteAddr("172.17.0.1")))
        .andExpect(status().isUnauthorized());
  }

  /** Covers A6: isLocalCaller accepts only loopback and exact extras. */
  @Test
  void isLocalCallerAcceptsOnlyLoopbackAndExactExtras() {
    Set<String> none = Set.of();
    assertThat(DevLocalAdminFilter.isLocalCaller("127.0.0.1", none)).isTrue();
    assertThat(DevLocalAdminFilter.isLocalCaller("::1", none)).isTrue();
    assertThat(DevLocalAdminFilter.isLocalCaller("0:0:0:0:0:0:0:1", none)).isTrue();
    assertThat(DevLocalAdminFilter.isLocalCaller("192.168.1.50", none)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller("127.0.0.2", none)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller("localhost", none)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller(null, none)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller("", none)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller("   ", none)).isFalse();
    Set<String> extras = Set.of("172.17.0.1");
    assertThat(DevLocalAdminFilter.isLocalCaller("172.17.0.1", extras)).isTrue();
    assertThat(DevLocalAdminFilter.isLocalCaller("172.17.0.10", extras)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller(null, extras)).isFalse();
    assertThat(DevLocalAdminFilter.isLocalCaller("", extras)).isFalse();
  }

  /** Covers A6: the extra-address setting is empty by default, so a private address is refused. */
  @Test
  void extraAddressesAreEmptyByDefault() throws Exception {
    mockMvc
        .perform(get("/api/admin/ping").with(remoteAddr("172.17.0.1")))
        .andExpect(status().isUnauthorized());
  }

  /** Covers A7 and A5: twin, with the local-caller check letting everything through. */
  @Test
  void twinNonLocalRemoteAddressIsAdminWhenTheLocalCheckIsOff() throws Exception {
    try (MockedStatic<DevLocalAdminFilter> guard =
        Mockito.mockStatic(DevLocalAdminFilter.class, Mockito.CALLS_REAL_METHODS)) {
      guard.when(() -> DevLocalAdminFilter.isLocalCaller(any(), any())).thenReturn(true);
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("192.168.1.50")))
          .andExpect(status().isOk());
    }
  }

  /** Covers A6: twin, the loopback-only rule is what refuses 10.0.0.1. */
  @Test
  void twinOtherAddressIsAdminWhenTheLoopbackRuleIsOff() throws Exception {
    try (MockedStatic<DevLocalAdminFilter> guard =
        Mockito.mockStatic(DevLocalAdminFilter.class, Mockito.CALLS_REAL_METHODS)) {
      guard.when(() -> DevLocalAdminFilter.isLocalCaller(any(), any())).thenReturn(true);
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("10.0.0.1")))
          .andExpect(status().isOk());
    }
  }

  /** Covers A15: a spoofed X-Forwarded-For does not make a remote caller local. */
  @Test
  void forwardedForHeaderDoesNotMakeARemoteCallerLocal() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/ping")
                .with(remoteAddr("192.168.1.50"))
                .header("X-Forwarded-For", "127.0.0.1")
                .header("X-Real-IP", "127.0.0.1")
                .header("Forwarded", "for=127.0.0.1"))
        .andExpect(status().isUnauthorized());
  }

  /** Covers A15: twin, with the local check off the same spoofed request gets through. */
  @Test
  void twinForwardedForRequestIsAdminWhenTheLocalCheckIsOff() throws Exception {
    try (MockedStatic<DevLocalAdminFilter> guard =
        Mockito.mockStatic(DevLocalAdminFilter.class, Mockito.CALLS_REAL_METHODS)) {
      guard.when(() -> DevLocalAdminFilter.isLocalCaller(any(), any())).thenReturn(true);
      mockMvc
          .perform(
              get("/api/admin/ping")
                  .with(remoteAddr("192.168.1.50"))
                  .header("X-Forwarded-For", "127.0.0.1"))
          .andExpect(status().isOk());
    }
  }

  /** Covers A15: a missing bootstrap row leaves a local request anonymous. */
  @Test
  void missingBootstrapRowLeavesLocalRequestAnonymous() throws Exception {
    when(appUserRepository.findByEmail(eq("admin@example.com"))).thenReturn(Optional.empty());
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
  }

  /** Covers A15: a non-admin bootstrap row leaves a local request anonymous. */
  @Test
  void nonAdminBootstrapRowLeavesLocalRequestAnonymous() throws Exception {
    when(appUserRepository.findByEmail(eq("admin@example.com")))
        .thenReturn(
            Optional.of(
                AppUserEntity.builder().id(1L).email("admin@example.com").isAdmin(false).build()));
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
  }

  /** Covers the interface: application-dev.properties maps the setting from the env variable. */
  @Test
  void devPropertiesMapTheExtraAddressesFromTheEnvVariable() throws Exception {
    String props = Files.readString(Path.of("src/main/resources/application-dev.properties"));
    assertThat(props)
        .contains("app.auth.dev-local-admin.extra-addresses=${DEV_LOCAL_ADMIN_EXTRA_ADDRESSES:}");
  }

  /** Covers the interface: docker-compose.yml passes the variable through with an empty default. */
  @Test
  void dockerComposePassesTheExtraAddressesThroughWithAnEmptyDefault() throws Exception {
    String compose = Files.readString(Path.of("docker-compose.yml"));
    assertThat(compose).contains("DEV_LOCAL_ADMIN_EXTRA_ADDRESSES");
    assertThat(compose)
        .containsPattern(
            "DEV_LOCAL_ADMIN_EXTRA_ADDRESSES\\s*[:=]\\s*\"?\\$\\{DEV_LOCAL_ADMIN_EXTRA_ADDRESSES:-?\\}");
  }

  /** Covers the interface: the filter javadoc names the documented variable. */
  @Test
  void filterJavadocNamesTheDocumentedEnvVariable() throws Exception {
    String src =
        Files.readString(
            Path.of("src/main/java/edens/zac/portfolio/backend/config/DevLocalAdminFilter.java"));
    assertThat(src).contains("DEV_LOCAL_ADMIN_EXTRA_ADDRESSES");
    assertThat(src).doesNotContain("APP_AUTH_DEV_LOCAL_ADMIN_EXTRA_ADDRESSES");
  }

  /** Covers A15: an address listed in the dev setting is treated as local. */
  @Nested
  @WebMvcTest(controllers = StubController.class)
  @TestPropertySource(
      properties = "app.auth.dev-local-admin.extra-addresses= 172.17.0.1 , 172.18.0.1")
  class ExtraAddresses {
    @Autowired private MockMvc mockMvc;

    @Autowired private AppUserRepository repository;

    @BeforeEach
    void adminRowExistsInThisContext() {
      when(repository.findByEmail(eq("admin@example.com")))
          .thenReturn(
              Optional.of(
                  AppUserEntity.builder().id(1L).email("admin@example.com").isAdmin(true).build()));
    }

    @Test
    void listedAddressesAreLocalAndOthersAreNot() throws Exception {
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("172.17.0.1")))
          .andExpect(status().isOk());
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("172.18.0.1")))
          .andExpect(status().isOk());
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("192.168.1.50")))
          .andExpect(status().isUnauthorized());
      mockMvc
          .perform(get("/api/admin/ping").with(remoteAddr("172.17.0.2")))
          .andExpect(status().isUnauthorized());
    }
  }

  /** Covers A15: a blank bootstrap email leaves even a loopback request anonymous. */
  @Nested
  @WebMvcTest(controllers = StubController.class)
  @TestPropertySource(properties = "app.auth.admin.bootstrap-email=")
  class BlankBootstrapEmail {
    @Autowired private MockMvc mockMvc;

    @Test
    void loopbackRequestStaysAnonymous() throws Exception {
      mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
    }
  }
}
