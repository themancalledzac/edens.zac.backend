package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.services.CollectionAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link CollaboratorAccessInterceptor} on /api/edit/**, honoring the same
 * app.admin.enforce-authz toggle as the /api/admin/** gate so local dev stays login-free.
 *
 * <p>Unlike {@link CacheControlWebConfig}, a missing dependency must NOT mean "no interceptor":
 * this is an authorization gate, and skipping registration would fail OPEN in sliced
 * {@code @WebMvcTest} contexts (which pick up WebMvcConfigurer beans but do not component-scan
 * services). When {@link CollectionAccessService} is absent, the surface is locked shut instead.
 */
@Configuration
class EditAccessWebConfig implements WebMvcConfigurer {

  private final ObjectProvider<CollectionAccessService> collectionAccessService;
  private final boolean enforceAuthz;

  EditAccessWebConfig(
      ObjectProvider<CollectionAccessService> collectionAccessService,
      @Value("${app.admin.enforce-authz:true}") boolean enforceAuthz) {
    this.collectionAccessService = collectionAccessService;
    this.enforceAuthz = enforceAuthz;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    if (!enforceAuthz) {
      return;
    }
    CollectionAccessService service = collectionAccessService.getIfAvailable();
    HandlerInterceptor gate =
        service != null
            ? new CollaboratorAccessInterceptor(service)
            : new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                throw new AccessDeniedException("Edit surface unavailable: access service missing");
              }
            };
    registry.addInterceptor(gate).addPathPatterns("/api/edit/**");
  }
}
