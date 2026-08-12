package edens.zac.portfolio.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link CacheControlInterceptor} across the public read surface ({@code /api/read/**}),
 * scoped exactly like {@link RequestMetricWebConfig}. Admin, auth, and public write endpoints are
 * excluded: they are neither cacheable nor served to anonymous callers.
 *
 * <p>Registered in every profile rather than prod-only, so the behaviour under test is the
 * behaviour that ships. The dev profile neutralises it through configuration instead, by setting
 * {@code app.cache.read-max-age-seconds=0} — see {@link ReadCachePolicy}.
 *
 * <p>The policy is taken from an {@link ObjectProvider} rather than a plain constructor dependency,
 * for the same reason {@link RequestMetricWebConfig} does it: sliced {@code @WebMvcTest} contexts
 * pick up {@code WebMvcConfigurer} beans but do not component-scan, so {@link ReadCachePolicy} is
 * absent there and a hard dependency would fail those contexts outright. When it is missing no
 * interceptor is registered — correct for a slice asserting authorization rather than freshness. In
 * the full application the policy is present and the interceptor is wired.
 */
@Configuration
@RequiredArgsConstructor
public class CacheControlWebConfig implements WebMvcConfigurer {

  private final ObjectProvider<ReadCachePolicy> readCachePolicy;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    ReadCachePolicy policy = readCachePolicy.getIfAvailable();
    if (policy != null) {
      registry.addInterceptor(new CacheControlInterceptor(policy)).addPathPatterns("/api/read/**");
    }
  }
}
