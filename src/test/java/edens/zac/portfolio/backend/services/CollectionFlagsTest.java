package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectionFlagsTest {

  private static CollectionEntity entity(boolean isClient, boolean isBlog) {
    CollectionEntity e = new CollectionEntity();
    e.setClient(isClient);
    e.setBlog(isBlog);
    return e;
  }

  @Test
  @DisplayName("create with neither flag lands on false/false")
  void forCreate_neither() {
    CollectionFlags.Resolved r = CollectionFlags.forCreate(null, null);
    assertThat(r.isClient()).isFalse();
    assertThat(r.isBlog()).isFalse();
  }

  @Test
  @DisplayName("create with isClient=true is a client gallery")
  void forCreate_client() {
    CollectionFlags.Resolved r = CollectionFlags.forCreate(true, null);
    assertThat(r.isClient()).isTrue();
    assertThat(r.isBlog()).isFalse();
  }

  @Test
  @DisplayName("create with isBlog=true is a blog")
  void forCreate_blog() {
    CollectionFlags.Resolved r = CollectionFlags.forCreate(null, true);
    assertThat(r.isClient()).isFalse();
    assertThat(r.isBlog()).isTrue();
  }

  @Test
  @DisplayName("both flags true is rejected")
  void forCreate_bothTrue_rejected() {
    assertThatThrownBy(() -> CollectionFlags.forCreate(true, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutually exclusive");
  }

  @Test
  @DisplayName("explicit true wins over the current state and clears the other flag")
  void forUpdate_explicitTrueWins() {
    CollectionFlags.Resolved r = CollectionFlags.forUpdate(null, true, entity(true, false));
    assertThat(r.isClient()).isFalse();
    assertThat(r.isBlog()).isTrue();
  }

  @Test
  @DisplayName("explicit false clears")
  void forUpdate_explicitFalseClears() {
    CollectionFlags.Resolved r = CollectionFlags.forUpdate(false, null, entity(true, false));
    assertThat(r.isClient()).isFalse();
    assertThat(r.isBlog()).isFalse();
  }

  @Test
  @DisplayName("null inherits the entity's current value")
  void forUpdate_nullInherits() {
    CollectionFlags.Resolved r = CollectionFlags.forUpdate(null, false, entity(true, false));
    assertThat(r.isClient()).isTrue();
    assertThat(r.isBlog()).isFalse();
  }

  @Test
  @DisplayName("null/null on an existing blog leaves it a blog")
  void forUpdate_bothNull_preserves() {
    CollectionFlags.Resolved r = CollectionFlags.forUpdate(null, null, entity(false, true));
    assertThat(r.isClient()).isFalse();
    assertThat(r.isBlog()).isTrue();
  }

  @Test
  @DisplayName("applyTo writes both flags onto the entity")
  void applyTo_writesBoth() {
    CollectionEntity e = entity(true, false);
    CollectionFlags.forUpdate(null, true, e).applyTo(e);
    assertThat(e.isClient()).isFalse();
    assertThat(e.isBlog()).isTrue();
  }

  @Test
  @DisplayName("Resolved rejects a both-true triple")
  void resolved_bothTrue_rejected() {
    assertThatThrownBy(() -> new CollectionFlags.Resolved(true, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutually exclusive");
  }
}
