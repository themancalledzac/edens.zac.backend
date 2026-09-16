package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local-dev stand-in for signing in. Under the {@code dev} profile only, an anonymous request to
 * {@code /api/admin/**} is authenticated as the bootstrap admin ({@code
 * app.auth.admin.bootstrap-email}) with {@code ROLE_USER} and {@code ROLE_ADMIN}, so the Lightroom
 * plugin and the local admin UI reach the admin surface without a session. The {@code
 * hasRole("ADMIN")} gate in {@link SecurityConfig} stays unconditional and every admin route still
 * sees a real user row behind {@code CurrentUser}.
 *
 * <p>Runs after {@link SessionAuthenticationFilter}, so a real session always wins, and before
 * {@link FlybySessionFilter}, so a share-link cookie in a local browser cannot lock the admin UI.
 * Fails closed: a blank bootstrap email, a missing row, or a row without {@code is_admin} leaves
 * the request anonymous and the gate answers 401 exactly as it does in every other profile.
 */
@Component
@Profile("dev")
@Slf4j
public class DevLocalAdminFilter extends OncePerRequestFilter {

  private static final String ADMIN_PATH_PREFIX = "/api/admin/";

  private final AppUserRepository appUserRepository;
  private final String bootstrapEmail;
  private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

  public DevLocalAdminFilter(
      AppUserRepository appUserRepository,
      @Value("${app.auth.admin.bootstrap-email:}") String bootstrapEmail) {
    this.appUserRepository = appUserRepository;
    this.bootstrapEmail = bootstrapEmail == null ? "" : bootstrapEmail.trim();
    if (this.bootstrapEmail.isEmpty()) {
      log.warn(
          "DevLocalAdminFilter is active but app.auth.admin.bootstrap-email is blank;"
              + " /api/admin/** stays closed to anonymous callers");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing == null || trustResolver.isAnonymous(existing)) {
      resolveBootstrapAdmin()
          .ifPresent(
              principal -> {
                var authorities =
                    List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
              });
    }
    filterChain.doFilter(request, response);
  }

  private Optional<AuthPrincipal> resolveBootstrapAdmin() {
    if (bootstrapEmail.isEmpty()) {
      return Optional.empty();
    }
    Optional<AppUserEntity> admin =
        appUserRepository.findByEmail(bootstrapEmail).filter(AppUserEntity::isAdmin);
    if (admin.isEmpty()) {
      log.warn(
          "DevLocalAdminFilter: no admin row for bootstrap email {}; request stays anonymous",
          bootstrapEmail);
    }
    return admin.map(u -> new AuthPrincipal(u.getId(), u.getEmail(), true, false));
  }
}
