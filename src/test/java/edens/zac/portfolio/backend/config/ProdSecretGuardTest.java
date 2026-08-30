package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.Application;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class ProdSecretGuardTest {

  private static final String REAL_SECRET =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String REAL_ACCESS_SECRET =
      "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

  private void invokeVerify(ProdSecretGuard guard) throws Exception {
    Method m = ProdSecretGuard.class.getDeclaredMethod("verify");
    m.setAccessible(true);
    try {
      m.invoke(guard);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      throw e;
    }
  }

  @Test
  void blankSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard("", REAL_ACCESS_SECRET, true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void nullSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(null, REAL_ACCESS_SECRET, true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void defaultDevSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard("dev-internal-secret", REAL_ACCESS_SECRET, true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void realSecretSucceeds() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, REAL_ACCESS_SECRET, true);

    assertThatCode(() -> invokeVerify(guard)).doesNotThrowAnyException();
  }

  @Test
  void blankAccessTokenSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, "", true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.access-token.secret");
  }

  @Test
  void nullAccessTokenSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, null, true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.access-token.secret");
  }

  @Test
  void defaultDevAccessTokenSecretThrows() {
    // S-11: this is the exact string docker-compose.yml falls back to, and it is printed in a
    // public repo. Mutation this catches: drop the access-token clause from verify().
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, "dev-access-token-secret", true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.access-token.secret");
  }

  @Test
  void enforceAuthzDisabledThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, REAL_ACCESS_SECRET, false);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.admin.enforce-authz");
  }

  /**
   * S-4. The tests above call {@code verify()} reflectively on a hand-built object, so none of them
   * can see {@code @PostConstruct} -- delete the annotation and the guard is dead at startup while
   * they all stay green. These boot a real context instead, so the container is what calls {@code
   * verify()}.
   *
   * <p>The context finds the guard the way the application does, by scanning {@link Application}'s
   * package. The runner used to be handed {@code ProdSecretGuard.class} directly, which left the
   * discovery half of the wiring untested: dropping {@code @Component}, or moving the class out of
   * the scanned tree, kept all five cases green while prod booted unguarded.
   *
   * <p>Mutations this catches: delete {@code @PostConstruct} and the three refusal cases redden;
   * delete {@code @Profile("prod")} and {@link #guardIsNotRegisteredOutsideProd} reddens; delete
   * {@code @Component}, or move the class out of {@code edens.zac.portfolio.backend}, and {@link
   * #prodStartsOnARealSecret} reddens on the missing bean, along with the three refusal cases.
   */
  @Nested
  class Wiring {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(ScanForTheGuard.class);

    /**
     * Stands in for the application's own component scan. {@code basePackageClasses} resolves to
     * {@link Application}'s package, the real scan root, so the guard has to be discoverable from
     * there rather than named by the test.
     *
     * <p>The exclude filter keeps every other bean out, which is what lets these cases run without
     * a datasource. It is a negative lookahead: it matches, and so excludes, every fully qualified
     * name that does not end in {@code .ProdSecretGuard}. Default filters stay on, so a candidate
     * still has to carry a stereotype annotation to be registered at all -- that is the half of the
     * wiring the old hand-registration could not see.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = Application.class,
        excludeFilters =
            @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "^(?!.*\\.ProdSecretGuard$).*$"))
    static class ScanForTheGuard {}

    private ApplicationContextRunner prodWith(String... properties) {
      return runner
          .withPropertyValues("spring.profiles.active=prod")
          .withPropertyValues(properties);
    }

    /** A prod context whose only defect is the one the calling test is about. */
    private ApplicationContextRunner prodWithGoodSecrets(String... properties) {
      return prodWith(
              "internal.api.secret=" + REAL_SECRET, "app.access-token.secret=" + REAL_ACCESS_SECRET)
          .withPropertyValues(properties);
    }

    @Test
    void prodRefusesToStartOnTheDefaultDevSecret() {
      prodWith(
              "internal.api.secret=dev-internal-secret",
              "app.access-token.secret=" + REAL_ACCESS_SECRET)
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("internal.api.secret");
              });
    }

    @Test
    void prodRefusesToStartWithTheAuthzGateOff() {
      prodWithGoodSecrets("app.admin.enforce-authz=false")
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.admin.enforce-authz");
              });
    }

    @Test
    void prodRefusesToStartOnTheDefaultDevAccessTokenSecret() {
      prodWith(
              "internal.api.secret=" + REAL_SECRET,
              "app.access-token.secret=dev-access-token-secret")
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.access-token.secret");
              });
    }

    /**
     * The control for the three above: without it, a context that failed for an unrelated reason,
     * or one where the bean was never registered at all, would read as a passing guard.
     */
    @Test
    void prodStartsOnARealSecret() {
      prodWithGoodSecrets()
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProdSecretGuard.class);
              });
    }

    @Test
    void guardIsNotRegisteredOutsideProd() {
      runner
          .withPropertyValues("internal.api.secret=dev-internal-secret")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ProdSecretGuard.class);
              });
    }
  }
}
