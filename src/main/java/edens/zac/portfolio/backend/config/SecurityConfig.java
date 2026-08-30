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

  /**
   * The chain's authorization tiers.
   *
   * <p>{@code hasRole("USER")} rather than {@code authenticated()} on every signed-in route: a
   * share-link (flyby) principal IS an {@code Authentication}, so {@code authenticated()} would
   * admit it. Every real authentication path grants {@code ROLE_USER} ({@link
   * SessionAuthenticationFilter} and {@code WebAuthnService.toAuthentication}) while {@link
   * FlybySessionFilter} grants no authorities at all, so this is behaviour-preserving for sessions
   * and fail-closed for link holders, enforced at the chain rather than in each controller.
   *
   * <p>{@code /api/admin/**} is the inner, app-layer gate: a session principal whose user row
   * carries {@code is_admin=true}. In prod it sits INSIDE the {@link InternalSecretFilter}
   * perimeter, so a request must both carry the BFF secret and resolve to an admin.
   *
   * <p>{@code /api/edit/**} is the collaborator tier. A real session is required here (401 for
   * anonymous, via the entry point); per-collection COLLABORATOR-or-above is enforced by {@link
   * CollaboratorAccessInterceptor} (403). {@code hasRole("USER")} for the same reason as above --
   * it keeps a flyby out of the edit surface ahead of the interceptor that would also deny it,
   * since a flyby caps at GENERAL and never reaches COLLABORATOR.
   *
   * <p>Both write tiers sat behind {@code app.admin.enforce-authz} until 2026-08-30, which let
   * local dev fall through to {@code permitAll}. That toggle is gone and the gate is unconditional
   * in every profile, which is what closes the null {@code CurrentUser.userId()} contract behind an
   * admin route.
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, SessionAuthenticationFilter saf, FlybySessionFilter flyby)
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
                  .hasRole("USER")
                  .requestMatchers("/api/auth/webauthn/register/**")
                  .hasRole("USER")
                  .requestMatchers("/api/read/user/**")
                  .hasRole("USER")
                  .requestMatchers("/api/admin/**")
                  .hasRole("ADMIN")
                  .requestMatchers("/api/edit/**")
                  .hasRole("USER")
                  .anyRequest()
                  .permitAll();
            })
        .addFilterBefore(saf, AuthorizationFilter.class)
        // After the session filter, and a no-op whenever it already resolved a principal: a real
        // session outranks a share link, so signing in never lands you in someone else's view.
        .addFilterAfter(flyby, SessionAuthenticationFilter.class)
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

  /**
   * Same suppression as {@link #sessionAuthenticationFilterRegistration}, for the same reason: the
   * {@code @Component} annotation would otherwise register FlybySessionFilter as a standalone
   * servlet filter in addition to its place in the SecurityFilterChain, running it twice per
   * request.
   */
  @Bean
  public FilterRegistrationBean<FlybySessionFilter> flybySessionFilterRegistration(
      FlybySessionFilter filter) {
    FilterRegistrationBean<FlybySessionFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
