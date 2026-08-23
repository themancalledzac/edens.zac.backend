package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

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

  @Test
  void enforceAuthzDisabledThrowsEvenWithAGoodSecret() {
    // The two checks are independent: a valid secret must not excuse an open authz gate.
    ProdSecretGuard guard = new ProdSecretGuard(REAL_SECRET, false);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("internal.api.secret");
  }
}
