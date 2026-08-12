package edens.zac.portfolio.backend.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessLevelTest {

  @Test
  void ranksAreExplicitAndOrdered() {
    assertThat(AccessLevel.GENERAL.rank()).isZero();
    assertThat(AccessLevel.CLIENT.rank()).isEqualTo(1);
    assertThat(AccessLevel.COLLABORATOR.rank()).isEqualTo(2);
    assertThat(AccessLevel.ADMIN.rank()).isEqualTo(3);
  }

  @Test
  void atLeastCoversAllSixteenOrderedPairs() {
    for (AccessLevel a : AccessLevel.values()) {
      for (AccessLevel b : AccessLevel.values()) {
        assertThat(a.atLeast(b)).as("%s.atLeast(%s)", a, b).isEqualTo(a.rank() >= b.rank());
      }
    }
  }

  @Test
  void adminOutranksEverythingAndGeneralOutranksNothingAboveIt() {
    for (AccessLevel level : AccessLevel.values()) {
      assertThat(AccessLevel.ADMIN.atLeast(level)).isTrue();
      assertThat(level.atLeast(AccessLevel.GENERAL)).isTrue();
    }
    assertThat(AccessLevel.GENERAL.atLeast(AccessLevel.CLIENT)).isFalse();
    assertThat(AccessLevel.CLIENT.atLeast(AccessLevel.COLLABORATOR)).isFalse();
    assertThat(AccessLevel.COLLABORATOR.atLeast(AccessLevel.ADMIN)).isFalse();
  }
}
