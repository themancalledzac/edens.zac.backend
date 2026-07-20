package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Unit tests for every {@link CollectionTypeCompat#resolve} rule branch. */
class CollectionTypeCompatTest {

  @Nested
  class BooleansProvided {

    @Test
    void isClientTrue_derivesClientGallery() {
      var resolved = CollectionTypeCompat.resolve(true, null, null, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void isBlogTrue_derivesBlog() {
      var resolved = CollectionTypeCompat.resolve(null, true, null, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void booleansWinOverProvidedType() {
      // Legacy type says MISC but the boolean says client gallery: booleans win.
      var resolved = CollectionTypeCompat.resolve(true, false, CollectionType.MISC, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
    }

    @Test
    void neitherTrue_currentlyClientGallery_foldsToMisc() {
      var resolved =
          CollectionTypeCompat.resolve(false, false, null, CollectionType.CLIENT_GALLERY);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void neitherTrue_currentlyBlog_foldsToMisc() {
      var resolved = CollectionTypeCompat.resolve(false, false, null, CollectionType.BLOG);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
    }

    @Test
    void neitherTrue_preservesNonClientBlogCurrentType() {
      var resolved = CollectionTypeCompat.resolve(false, false, null, CollectionType.PORTFOLIO);
      assertThat(resolved.type()).isEqualTo(CollectionType.PORTFOLIO);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void neitherTrue_requestedTypeWinsOverCurrentType() {
      // A request carrying both booleans (both false) and a legacy type keeps the requested type
      // when it does not conflict with the flags.
      var resolved =
          CollectionTypeCompat.resolve(
              false, false, CollectionType.ART_GALLERY, CollectionType.MISC);
      assertThat(resolved.type()).isEqualTo(CollectionType.ART_GALLERY);
    }

    @Test
    void neitherTrue_requestedClientGalleryTypeFoldsToMisc() {
      // Booleans disclaim client/blog, so a legacy CLIENT_GALLERY type request folds to MISC.
      var resolved =
          CollectionTypeCompat.resolve(false, false, CollectionType.CLIENT_GALLERY, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
    }

    @Test
    void createWithNeitherBooleanTrueAndNoType_landsOnMisc() {
      var resolved = CollectionTypeCompat.resolve(false, false, null, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void singleFalseFlagCountsAsProvided() {
      // isClient=false alone (isBlog absent) still enters the booleans-win path: flags were
      // provided and neither is true, so a stored BLOG type folds to MISC.
      var resolved = CollectionTypeCompat.resolve(false, null, null, CollectionType.BLOG);
      assertThat(resolved.type()).isEqualTo(CollectionType.MISC);
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void bothTrue_isRejectedWith400Semantics() {
      assertThatThrownBy(() -> CollectionTypeCompat.resolve(true, true, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("mutually exclusive");
    }
  }

  @Nested
  class LegacyTypeOnly {

    @Test
    void clientGalleryType_derivesIsClient() {
      var resolved = CollectionTypeCompat.resolve(null, null, CollectionType.CLIENT_GALLERY, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.CLIENT_GALLERY);
      assertThat(resolved.isClient()).isTrue();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void blogType_derivesIsBlog() {
      var resolved = CollectionTypeCompat.resolve(null, null, CollectionType.BLOG, null);
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = CollectionType.class,
        names = {"PORTFOLIO", "ART_GALLERY", "HOME", "PARENT", "MISC"})
    void otherTypes_deriveFalseFalse(CollectionType type) {
      var resolved = CollectionTypeCompat.resolve(null, null, type, null);
      assertThat(resolved.type()).isEqualTo(type);
      assertThat(resolved.isClient()).isFalse();
      assertThat(resolved.isBlog()).isFalse();
    }

    @Test
    void noTypeAndNoBooleans_fallsBackToCurrentType() {
      var resolved = CollectionTypeCompat.resolve(null, null, null, CollectionType.BLOG);
      assertThat(resolved.type()).isEqualTo(CollectionType.BLOG);
      assertThat(resolved.isBlog()).isTrue();
    }

    @Test
    void nothingProvidedAtAll_landsOnMisc() {
      var resolved = CollectionTypeCompat.resolve(null, null, null, null);
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
}
