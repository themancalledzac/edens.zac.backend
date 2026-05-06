package edens.zac.portfolio.backend.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CollectionVisibilityTest {

  @Test
  void valuesAreListedUnlistedHidden() {
    assertThat(CollectionVisibility.values())
        .containsExactly(
            CollectionVisibility.LISTED,
            CollectionVisibility.UNLISTED,
            CollectionVisibility.HIDDEN);
  }

  @Test
  void forValueAcceptsExactName() {
    assertThat(CollectionVisibility.forValue("LISTED")).isEqualTo(CollectionVisibility.LISTED);
    assertThat(CollectionVisibility.forValue("unlisted")).isEqualTo(CollectionVisibility.UNLISTED);
  }

  @Test
  void forValueRejectsBlank() {
    assertThatThrownBy(() -> CollectionVisibility.forValue(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CollectionVisibility.forValue(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void appearsInListsTrueOnlyForListed() {
    assertThat(CollectionVisibility.LISTED.appearsInLists()).isTrue();
    assertThat(CollectionVisibility.UNLISTED.appearsInLists()).isFalse();
    assertThat(CollectionVisibility.HIDDEN.appearsInLists()).isFalse();
  }

  @Test
  void requiresLocalEnvOnlyForHidden() {
    assertThat(CollectionVisibility.HIDDEN.requiresLocalEnv()).isTrue();
    assertThat(CollectionVisibility.LISTED.requiresLocalEnv()).isFalse();
    assertThat(CollectionVisibility.UNLISTED.requiresLocalEnv()).isFalse();
  }
}
