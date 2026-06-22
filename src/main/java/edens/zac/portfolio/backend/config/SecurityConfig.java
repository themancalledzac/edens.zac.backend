package edens.zac.portfolio.backend.config;

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
  public SecurityFilterChain filterChain(HttpSecurity http, SessionAuthenticationFilter saf)
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
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/auth/webauthn/login/**")
                    .permitAll()
                    .requestMatchers("/api/auth/me", "/api/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/auth/webauthn/register/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
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
