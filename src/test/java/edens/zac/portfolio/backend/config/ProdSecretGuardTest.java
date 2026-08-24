package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProdSecretGuardTest {

  private static final String REAL_SECRET =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
    ProdSecretGuard guard = new ProdSecretGuard("", true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void nullSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(null, true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void defaultDevSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard("dev-internal-secret", true);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void realSecretSucceeds() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, true);

    assertThatCode(() -> invokeVerify(guard)).doesNotThrowAnyException();
  }

  @Test
  void enforceAuthzDisabledThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, false);

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
   * <p>Delete {@code @PostConstruct} and both failure cases redden. Delete {@code @Profile("prod")}
   * and {@link #guardIsNotRegisteredOutsideProd} reddens.
   */
  @Nested
  class Wiring {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(ProdSecretGuard.class);

    private ApplicationContextRunner prodWith(String... properties) {
      return runner
          .withPropertyValues("spring.profiles.active=prod")
          .withPropertyValues(properties);
    }

    @Test
    void prodRefusesToStartOnTheDefaultDevSecret() {
      prodWith("internal.api.secret=dev-internal-secret")
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
      prodWith("internal.api.secret=" + REAL_SECRET, "app.admin.enforce-authz=false")
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.admin.enforce-authz");
              });
    }

    /**
     * The control for the two above: without it, a context that failed for an unrelated reason, or
     * one where the bean was never registered at all, would read as a passing guard.
     */
    @Test
    void prodStartsOnARealSecret() {
      prodWith("internal.api.secret=" + REAL_SECRET)
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
