package edens.zac.portfolio.backend.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import edens.zac.portfolio.backend.types.AccessLevel;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A share-link holder on the REAL security chain. The unit tests pin that a flyby resolves to at
 * most GENERAL; this pins what that actually buys and costs at the HTTP edge, with the matchers,
 * the filter order and CollaboratorAccessInterceptor all live.
 *
 * <p>The interesting case is {@code /api/edit/**}. Before this feature those routes were {@code
 * .authenticated()}, and a flyby IS an Authentication -- so the matcher would have admitted one and
 * left the interceptor as the only thing standing between a link holder and the edit surface.
 * RoleRepository is the only mock below the seam, so the real CollectionAccessService resolution
 * runs.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  FlybySessionFilter.class,
  EditAccessWebConfig.class,
  CollectionAccessService.class,
  FlybyAccessWebMvcTest.StubControllers.class
})
class FlybyAccessWebMvcTest {

  private static final Cookie FLYBY_COOKIE = new Cookie(FlybyCookies.COOKIE_NAME, "raw-token");

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private ShareLinkService shareLinkService;
  @MockBean private RoleRepository roleRepository;

  private void resolvingShareLink() {
    when(shareLinkService.resolveByRawToken("raw-token"))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).userId(7L).build()));
  }

  @Test
  void flybyIsRejectedFromTheEditSurfaceEvenWithScopeOnThatCollection() throws Exception {
    resolvingShareLink();
    // In scope for reading -- and still refused, because editing needs COLLABORATOR.
    when(shareLinkService.levelFor(42L, 5L)).thenReturn(Optional.of(AccessLevel.GENERAL));

    // 403, not 401: the hasRole("USER") matcher rejects it before CollaboratorAccessInterceptor
    // is even consulted. Under the old .authenticated() matcher this request would have passed
    // the chain and left the interceptor as the only thing in the way.
    mockMvc
        .perform(get("/api/edit/collections/5/ping").cookie(FLYBY_COOKIE))
        .andExpect(status().isForbidden());
  }

  @Test
  void flybyIsRejectedFromTheAdminSurface() throws Exception {
    resolvingShareLink();

    // 403 rather than 401: the flyby IS authenticated, it just holds no authority. Getting 401
    // here would mean the principal never resolved and the request was merely anonymous.
    mockMvc.perform(get("/api/admin/ping").cookie(FLYBY_COOKIE)).andExpect(status().isForbidden());
  }

  @Test
  void theFlybyPrincipalActuallyResolvesOnTheRealChain() throws Exception {
    resolvingShareLink();

    // Guards every other assertion in this class. The filter sits after
    // AnonymousAuthenticationFilter, so the context is already non-null when it runs; an
    // "is anything authenticated yet" check that treats anonymous as a session leaves every
    // share link silently resolving to anonymous, and each deny below would still pass.
    mockMvc
        .perform(get("/api/read/whoami").cookie(FLYBY_COOKIE))
        .andExpect(status().isOk())
        .andExpect(content().string("share:42"));
  }

  @Test
  void flybyIsRejectedFromMe() throws Exception {
    resolvingShareLink();

    mockMvc.perform(get("/api/auth/me").cookie(FLYBY_COOKIE)).andExpect(status().isForbidden());
  }

  @Test
  void flybyStillReachesThePublicReadSurface() throws Exception {
    resolvingShareLink();

    // The point of the whole feature: a link holder browses, they are just capped while doing it.
    mockMvc.perform(get("/api/read/ping").cookie(FLYBY_COOKIE)).andExpect(status().isOk());
  }

  @Test
  void anUnresolvableTokenIsSimplyAnonymous() throws Exception {
    when(shareLinkService.resolveByRawToken("raw-token")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/read/ping").cookie(FLYBY_COOKIE)).andExpect(status().isOk());
    // 401 here versus 403 for a resolved flyby: the status difference is the tell that a stale
    // cookie produced no principal at all rather than an authenticated-but-unauthorized one.
    mockMvc
        .perform(get("/api/edit/collections/5/ping").cookie(FLYBY_COOKIE))
        .andExpect(status().isUnauthorized());
  }

  @Configuration
  static class StubControllers {
    @Bean
    StubController stubController() {
      return new StubController();
    }
  }

  @RestController
  static class StubController {
    @GetMapping("/api/read/ping")
    String read() {
      return "pong";
    }

    @GetMapping("/api/read/whoami")
    String whoami(@AuthenticationPrincipal AuthPrincipal principal) {
      return principal == null || principal.shareId() == null
          ? "anonymous"
          : "share:" + principal.shareId();
    }

    @GetMapping("/api/admin/ping")
    String admin() {
      return "pong";
    }

    @GetMapping("/api/auth/me")
    String me() {
      return "pong";
    }

    @GetMapping("/api/edit/collections/{collectionId}/ping")
    String edit(@PathVariable Long collectionId) {
      return "pong";
    }
  }
}
