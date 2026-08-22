package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProdSecretGuardTest {

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
    ProdSecretGuard guard = new ProdSecretGuard("");

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void nullSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard(null);

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void defaultDevSecretThrows() {
    ProdSecretGuard guard = new ProdSecretGuard("dev-internal-secret");

    assertThatThrownBy(() -> invokeVerify(guard))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal.api.secret");
  }

  @Test
  void realSecretSucceeds() {
    ProdSecretGuard guard =
        new ProdSecretGuard("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    assertThatCode(() -> invokeVerify(guard)).doesNotThrowAnyException();
  }
}
