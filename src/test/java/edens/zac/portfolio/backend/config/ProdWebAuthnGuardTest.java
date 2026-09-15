package edens.zac.portfolio.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProdWebAuthnGuardTest {

  @Test
  void refusesTheLocalhostRelyingPartyId() {
    var guard = new ProdWebAuthnGuard("localhost", "https://zacedens.com");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void refusesABlankRelyingPartyId() {
    var guard = new ProdWebAuthnGuard("", "https://zacedens.com");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void refusesOriginsThatAreAllLocalhost() {
    var guard =
        new ProdWebAuthnGuard("zacedens.com", "http://localhost:3000,http://localhost:3001");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void acceptsTheProductionDomainAndOrigins() {
    var guard =
        new ProdWebAuthnGuard("zacedens.com", "https://zacedens.com,https://www.zacedens.com");
    assertDoesNotThrow(guard::verify);
  }
}
