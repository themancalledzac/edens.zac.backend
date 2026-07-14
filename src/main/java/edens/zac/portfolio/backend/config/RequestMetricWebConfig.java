package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link RequestMetricInterceptor} for the public read surface only ({@code
 * /api/read/**}). Recording is deliberately scoped there: those are the cacheable content reads the
 * "what can we learn from our API requests" datapoint is about. Admin, auth, and public write
 * endpoints are excluded.
 *
 * <p>The interceptor is built here from an {@link ObjectProvider} for {@link
 * RequestMetricRepository} rather than being a {@code @Component}. This keeps sliced
 * {@code @WebMvcTest} contexts loading cleanly: those slices pick up {@code
 * HandlerInterceptor}/{@code WebMvcConfigurer} beans but have no DAO layer, so the repository is
 * absent and no interceptor is registered. In the full application the repository is present and
 * the interceptor is wired.
 */
@Configuration
@RequiredArgsConstructor
public class RequestMetricWebConfig implements WebMvcConfigurer {

  private final ObjectProvider<RequestMetricRepository> requestMetricRepository;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    RequestMetricRepository repository = requestMetricRepository.getIfAvailable();
    if (repository != null) {
      registry
          .addInterceptor(new RequestMetricInterceptor(repository))
          .addPathPatterns("/api/read/**");
    }
  }
}
