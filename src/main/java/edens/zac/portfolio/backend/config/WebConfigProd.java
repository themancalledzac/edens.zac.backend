package edens.zac.portfolio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("prod")
@Slf4j
class WebConfigProd implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    log.info("Configuring CORS for prod profile - no origins allowed (server-to-server only)");
    // No CORS origins configured for production.
    // All browser traffic goes through the Next.js BFF proxy,
    // so direct browser-to-backend CORS is not needed.
  }
}
