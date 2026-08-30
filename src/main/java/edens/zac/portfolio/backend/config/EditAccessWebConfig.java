package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.services.CollectionAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link CollaboratorAccessInterceptor} on /api/edit/**.
 *
 * <p>Unlike {@link CacheControlWebConfig}, a missing dependency must NOT mean "no interceptor":
 * this is an authorization gate, and skipping registration would fail OPEN in sliced
 * {@code @WebMvcTest} contexts (which pick up WebMvcConfigurer beans but do not component-scan
 * services). When {@link CollectionAccessService} is absent, the surface is locked shut instead.
 */
@Configuration
public class EditAccessWebConfig implements WebMvcConfigurer {

  private final ObjectProvider<CollectionAccessService> collectionAccessService;

  EditAccessWebConfig(ObjectProvider<CollectionAccessService> collectionAccessService) {
    this.collectionAccessService = collectionAccessService;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
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
