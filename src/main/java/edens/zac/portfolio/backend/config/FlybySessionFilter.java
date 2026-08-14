package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.ShareLinkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the {@code ezac_flyby} cookie into a share-link principal, so a link recipient keeps
 * their view while navigating the site rather than being confined to the page the link opened.
 *
 * <p>Runs after {@link SessionAuthenticationFilter} and returns immediately when an {@code
 * Authentication} is already present, so a real session always wins. Signing in can never drop a
 * user into somebody else's shared view.
 *
 * <p>Grants NO authorities. A flyby therefore fails every {@code hasRole} rule, and the {@code
 * hasRole("USER")} matchers in {@link SecurityConfig} reject it from the authenticated surface at
 * the chain rather than relying on each controller to notice. Its read access comes solely from
 * {@code CollectionAccessService.effectiveLevel}, which caps a share principal at GENERAL inside
 * the share's scope and denies outside it.
 *
 * <p>Deliberately side-effect free: it neither refreshes the cookie nor touches {@code
 * last_used_at}. Re-issuing a Set-Cookie here would attach one to every response, including the
 * cacheable {@code /api/read/**} bodies that CloudFront fronts -- a cookie on a cached response can
 * be served to the wrong viewer. The rolling refresh happens only on the share endpoints, which are
 * not cacheable.
 */
@Component
@RequiredArgsConstructor
public class FlybySessionFilter extends OncePerRequestFilter {

  private final ShareLinkService shareLinkService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      String rawToken = FlybyCookies.read(request);
      if (rawToken != null) {
        shareLinkService
            .resolveByRawToken(rawToken)
            .ifPresent(
                link -> {
                  var auth =
                      new UsernamePasswordAuthenticationToken(
                          AuthPrincipal.flyby(link.getId()), null, List.of());
                  SecurityContext context = SecurityContextHolder.createEmptyContext();
                  context.setAuthentication(auth);
                  SecurityContextHolder.setContext(context);
                });
      }
    }
    filterChain.doFilter(request, response);
  }
}
