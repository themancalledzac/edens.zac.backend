package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Unit tests for every {@link CollectionTypeCompat} resolution rule branch. */
class CollectionTypeCompatTest {

  /** An undrifted entity: flags in sync with the legacy type, as every write leaves them. */
  private static CollectionEntity current(CollectionType type) {
    return current(type, type == CollectionType.CLIENT_GALLERY, type == CollectionType.BLOG);
  }

  /** An entity with explicit flags, used to drive the drifted-row cases. */
  private static CollectionEntity current(CollectionType type, boolean isClient, boolean isBlog) {
    return CollectionEntity.builder().type(type).isClient(isClient).isBlog(isBlog).build();
  }

  @Nested
  class BooleansProvided {

    @Test
    void isClientTrue_derivesClientGallery() {
      var resolved = CollectionTypeCompat.forCreate(true, null, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void isBlogTrue_derivesBlog() {
      var resolved = CollectionTypeCompat.forCreate(null, true, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void booleansWinOverProvidedType() {
      // Legacy type says MISC but the boolean says client gallery: booleans win.
      var resolved = CollectionTypeCompat.forCreate(true, false, CollectionType.MISC);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
    }

    @Test
    void neitherTrue_currentlyClientGallery_foldsToMisc() {
      var resolved =
          CollectionTypeCompat.forUpdate(
              false, false, null, current(CollectionType.CLIENT_GALLERY));
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void neitherTrue_currentlyBlog_foldsToMisc() {
      var resolved =
          CollectionTypeCompat.forUpdate(false, false, null, current(CollectionType.BLOG));
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
    }

    @Test
    void neitherTrue_preservesNonClientBlogCurrentType() {
      var resolved =
          CollectionTypeCompat.forUpdate(false, false, null, current(CollectionType.PORTFOLIO));
      assertThat(resolved.type()).isEqualTo(CollectionType.PORTFOLIO);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void neitherTrue_requestedTypeWinsOverCurrentType() {
      // A request carrying both booleans (both false) and a legacy type keeps the requested type
      // when it does not conflict with the flags.
      var resolved =
          CollectionTypeCompat.forUpdate(
              false, false, CollectionType.ART_GALLERY, current(CollectionType.MISC));
      assertThat(resolved.type()).isEqualTo(CollectionType.ART_GALLERY);
    }

    @Test
    void neitherTrue_requestedClientGalleryTypeFoldsToMisc() {
      // Booleans disclaim client/blog, so a legacy CLIENT_GALLERY type request folds to MISC.
      var resolved = CollectionTypeCompat.forCreate(false, false, CollectionType.CLIENT_GALLERY);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
    }

    @Test
    void createWithNeitherBooleanTrueAndNoType_landsOnMisc() {
      var resolved = CollectionTypeCompat.forCreate(false, false, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void nullFlagInheritsFromCurrentType_partialUpdateDoesNotDemote() {
      // isClient=false alone (isBlog absent) leaves isBlog untouched: it inherits true from
      // the stored blog flag, so the collection stays a blog.
      var resolved =
          CollectionTypeCompat.forUpdate(false, null, null, current(CollectionType.BLOG));
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void partialIsBlogFalse_onClientGallery_staysClientGallery() {
      // The partial-update contract: {"isBlog": false} on a CLIENT_GALLERY must not clear the
      // untouched isClient flag (which would silently demote the collection to MISC).
      var resolved =
          CollectionTypeCompat.forUpdate(null, false, null, current(CollectionType.CLIENT_GALLERY));
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void untouchedFlagInheritsTheEntityBooleanNotTheLegacyType() {
      // Drifted row (type=MISC but is_blog=true -- the shape a rollback or out-of-band SQL
      // leaves behind). {"isClient": false} must not clear the untouched is_blog. Inheriting
      // from the type column instead would demote the row on every update, and would become
      // an unconditional demotion once phase 2 nulls the type column.
      var resolved =
          CollectionTypeCompat.forUpdate(
              false, null, null, current(CollectionType.MISC, false, true));
      assertThat(resolved.isBlog()).isTrue();
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
    }

    @Test
    void explicitTrueClearsTheOtherInheritedFlag() {
      // Setting isBlog=true on a CLIENT_GALLERY switches category: the untouched isClient
      // would inherit true, but an explicit true wins and clears it (no 400).
      var resolved =
          CollectionTypeCompat.forUpdate(null, true, null, current(CollectionType.CLIENT_GALLERY));
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void explicitFalseOnTheEncodingFlagDemotesToMisc() {
      // isClient=false alone on a CLIENT_GALLERY explicitly clears the flag that encoded the
      // type; the untouched isBlog inherits false, so the collection folds to MISC.
      var resolved =
          CollectionTypeCompat.forUpdate(false, null, null, current(CollectionType.CLIENT_GALLERY));
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void nullFlagInheritsFromRequestedTypeOverCurrentType() {
      // "Unless type is set": a request carrying a legacy type derives the untouched flag from
      // that type, not from the entity's current flags.
      var resolved =
          CollectionTypeCompat.forUpdate(
              null, false, CollectionType.CLIENT_GALLERY, current(CollectionType.MISC));
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void bothTrue_isRejectedWith400Semantics() {
      assertThatThrownBy(() -> CollectionTypeCompat.forCreate(true, true, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("mutually exclusive");
    }

    @ParameterizedTest
    @EnumSource(
        value = CollectionType.class,
        names = {"PARENT", "HOME"})
    void explicitTrueOnParentType_isRejected(CollectionType parentType) {
      // One checkbox must not destroy the structural type that isParentType() gates across the
      // service layer (~10 call sites), nor re-open a parent to non-collection content.
      assertThatThrownBy(
              () -> CollectionTypeCompat.forUpdate(true, null, null, current(parentType)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(parentType.name());
      assertThatThrownBy(
              () -> CollectionTypeCompat.forUpdate(null, true, null, current(parentType)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(parentType.name());
    }

    @Test
    void explicitTrueWithExplicitRetypeAwayFromParent_isAllowed() {
      // Retyping a parent stays deliberate: naming the target type in the same request moves the
      // effective base off the parent type, so the flag is accepted.
      var resolved =
          CollectionTypeCompat.forUpdate(
              true, null, CollectionType.CLIENT_GALLERY, current(CollectionType.PARENT));
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
    }
  }

  @Nested
  class LegacyTypeOnly {

    @Test
    void clientGalleryType_derivesIsClient() {
      var resolved = CollectionTypeCompat.forCreate(null, null, CollectionType.CLIENT_GALLERY);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void blogType_derivesIsBlog() {
      var resolved = CollectionTypeCompat.forCreate(null, null, CollectionType.BLOG);
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = CollectionType.class,
        names = {"PORTFOLIO", "ART_GALLERY", "HOME", "PARENT", "MISC"})
    void otherTypes_deriveFalseFalse(CollectionType type) {
      var resolved = CollectionTypeCompat.forCreate(null, null, type);
      assertThat(resolved.type()).isEqualTo(type);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void noTypeAndNoBooleans_fallsBackToCurrentType() {
      var resolved = CollectionTypeCompat.forUpdate(null, null, null, current(CollectionType.BLOG));
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void nothingProvidedAtAll_landsOnMisc() {
      var resolved = CollectionTypeCompat.forCreate(null, null, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }
  }

  @Nested
  class DeriveHelpers {

    @Test
    void deriveIsClient_onlyForClientGallery() {
      assertThat(CollectionTypeCompat.deriveIsClient(CollectionType.CLIENT_GALLERY)).isTrue();
      assertThat(CollectionTypeCompat.deriveIsClient(CollectionType.BLOG)).isFalse();
      assertThat(CollectionTypeCompat.deriveIsClient(CollectionType.MISC)).isFalse();
    }

    @Test
    void deriveIsBlog_onlyForBlog() {
      assertThat(CollectionTypeCompat.deriveIsBlog(CollectionType.BLOG)).isTrue();
      assertThat(CollectionTypeCompat.deriveIsBlog(CollectionType.CLIENT_GALLERY)).isFalse();
      assertThat(CollectionTypeCompat.deriveIsBlog(CollectionType.PARENT)).isFalse();
    }
  }

  @Nested
  class ResolvedInvariant {

    @Test
    void inconsistentFlagsAreRejected() {
      // The record is the last line of defence: the javadoc promises the triple is always
      // mutually consistent, and the entity setters trust it blindly.
      assertThatThrownBy(() -> new CollectionTypeCompat.Resolved(CollectionType.BLOG, true, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("inconsistent");
    }
  }
}
