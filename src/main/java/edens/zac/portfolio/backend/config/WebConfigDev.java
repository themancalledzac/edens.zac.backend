package edens.zac.portfolio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev")
@Slf4j
class WebConfigDev implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    log.info("Configuring CORS for dev profile");
    registry
        .addMapping("/api/**")
        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
