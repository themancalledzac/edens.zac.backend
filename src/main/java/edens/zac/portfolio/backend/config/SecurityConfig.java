package edens.zac.portfolio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      SessionAuthenticationFilter saf,
      @Value("${app.admin.enforce-authz:true}") boolean enforceAdminAuthz)
      throws Exception {
    http
        // CSRF defense for the API is provided by SameSite=Strict cookies + the BFF write-method
        // Origin allowlist; the stateless API has no server-side CSRF token to validate.
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(HttpMethod.POST, "/api/auth/login")
                  .permitAll()
                  .requestMatchers("/api/auth/webauthn/login/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/auth/invite/*")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/api/auth/invite/*/accept")
                  .permitAll()
                  .requestMatchers("/api/auth/me", "/api/auth/logout")
                  .authenticated()
                  .requestMatchers("/api/auth/webauthn/register/**")
                  .authenticated();
              // /api/admin/** is the inner, app-layer gate. When enforce-authz is on (prod, and
              // the default everywhere else), these routes require a session principal whose user
              // row carries is_admin=true — ROLE_ADMIN, granted by SessionAuthenticationFilter.
              // In prod this sits INSIDE the InternalSecretFilter perimeter: a request must both
              // carry the BFF secret AND resolve to an admin. The toggle is flipped off only in
              // application-dev.properties, where /api/admin/** then falls through to permitAll
              // below so local dev stays login-free (mirrors InternalSecretFilter being prod-only).
              if (enforceAdminAuthz) {
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
              }
              auth.anyRequest().permitAll();
            })
        .addFilterBefore(saf, AuthorizationFilter.class)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(401)));
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  /**
   * Suppress Spring Boot's automatic standalone servlet-filter registration of
   * SessionAuthenticationFilter. Without this, the @Component annotation causes the filter to be
   * registered as a raw servlet filter for ALL requests AND also inside the SecurityFilterChain via
   * addFilterBefore — running it twice. Disabling the auto-registration ensures the filter runs
   * exactly once, inside the chain only.
   */
  @Bean
  public FilterRegistrationBean<SessionAuthenticationFilter>
      sessionAuthenticationFilterRegistration(SessionAuthenticationFilter filter) {
    FilterRegistrationBean<SessionAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
