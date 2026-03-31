package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edens.zac.portfolio.backend.services.ImageMetadata.BooleanExtractor;
import edens.zac.portfolio.backend.services.ImageMetadata.ExifTags;
import edens.zac.portfolio.backend.services.ImageMetadata.MetadataField;
import edens.zac.portfolio.backend.services.ImageMetadata.NumericExtractor;
import edens.zac.portfolio.backend.services.ImageMetadata.ShutterSpeedExtractor;
import edens.zac.portfolio.backend.services.ImageMetadata.SimpleStringExtractor;
import edens.zac.portfolio.backend.services.ImageMetadata.XmpProperty;
import org.junit.jupiter.api.Test;

class ImageMetadataTest {

  // ==================== XMP Fallback Ordering ====================

  @Test
  void createDate_hasFourXmpFallbackEntries() {
    XmpProperty xmpProperty = MetadataField.CREATE_DATE.getXmpProperty();

    assertNotNull(xmpProperty);
    assertTrue(xmpProperty.hasProperties());
    assertEquals(4, xmpProperty.getEntries().size());
  }

  @Test
  void createDate_firstFallbackIsDateTimeOriginal() {
    XmpProperty xmpProperty = MetadataField.CREATE_DATE.getXmpProperty();
    XmpProperty.NamespaceProp first = xmpProperty.getEntries().get(0);

    assertEquals("DateTimeOriginal", first.propertyName());
  }

  @Test
  void createDate_fallbackOrderPreservesInsertionOrder() {
    XmpProperty xmpProperty = MetadataField.CREATE_DATE.getXmpProperty();
    var entries = xmpProperty.getEntries();

    assertEquals("DateTimeOriginal", entries.get(0).propertyName());
    assertEquals("DateTimeDigitized", entries.get(1).propertyName());
    assertEquals("CreateDate", entries.get(2).propertyName());
    assertEquals("DateCreated", entries.get(3).propertyName());
  }

  // ==================== ExifTags ====================

  @Test
  void exifTags_matchesIgnoresCase() {
    ExifTags tags = ExifTags.of("ISO Speed Ratings", "ISO");

    assertTrue(tags.matches("iso speed ratings"));
    assertTrue(tags.matches("ISO"));
    assertTrue(tags.matches("iso"));
  }

  @Test
  void exifTags_noMatchReturnsFalse() {
    ExifTags tags = ExifTags.of("ISO Speed Ratings");

    assertFalse(tags.matches("Shutter Speed"));
  }

  @Test
  void exifTags_none_hasNoTagNames() {
    ExifTags tags = ExifTags.none();

    assertFalse(tags.hasTagNames());
    assertFalse(tags.matches("anything"));
  }

  // ==================== XmpProperty ====================

  @Test
  void xmpProperty_none_hasNoProperties() {
    XmpProperty xmp = XmpProperty.none();

    assertFalse(xmp.hasProperties());
    assertTrue(xmp.getEntries().isEmpty());
  }

  @Test
  void xmpProperty_of_createsSingleEntry() {
    XmpProperty xmp = XmpProperty.of("http://ns.example.com/", "TestProp");

    assertTrue(xmp.hasProperties());
    assertEquals(1, xmp.getEntries().size());
    assertEquals("http://ns.example.com/", xmp.getEntries().get(0).namespace());
    assertEquals("TestProp", xmp.getEntries().get(0).propertyName());
  }

  // ==================== SimpleStringExtractor ====================

  @Test
  void simpleStringExtractor_trimsWhitespace() {
    var extractor = new SimpleStringExtractor();

    assertEquals("hello", extractor.extract("  hello  "));
  }

  @Test
  void simpleStringExtractor_returnsNullForNull() {
    var extractor = new SimpleStringExtractor();

    assertNull(extractor.extract(null));
  }

  @Test
  void simpleStringExtractor_returnsNullForEmpty() {
    var extractor = new SimpleStringExtractor();

    assertNull(extractor.extract(""));
  }

  @Test
  void simpleStringExtractor_returnsNullForBlank() {
    var extractor = new SimpleStringExtractor();

    assertNull(extractor.extract("   "));
  }

  @Test
  void simpleStringExtractor_preservesInternalSpaces() {
    var extractor = new SimpleStringExtractor();

    assertEquals("Canon EOS R5", extractor.extract("Canon EOS R5"));
  }

  // ==================== NumericExtractor ====================

  @Test
  void numericExtractor_stripsNonNumericPrefix() {
    var extractor = new NumericExtractor();

    assertEquals("400", extractor.extract("ISO 400"));
  }

  @Test
  void numericExtractor_preservesDecimals() {
    var extractor = new NumericExtractor();

    assertEquals("2.8", extractor.extract("f/2.8"));
  }

  @Test
  void numericExtractor_returnsNullForAllNonNumeric() {
    var extractor = new NumericExtractor();

    assertNull(extractor.extract("no numbers here"));
  }

  @Test
  void numericExtractor_returnsNullForNull() {
    var extractor = new NumericExtractor();

    assertNull(extractor.extract(null));
  }

  @Test
  void numericExtractor_returnsNullForEmpty() {
    var extractor = new NumericExtractor();

    assertNull(extractor.extract(""));
  }

  @Test
  void numericExtractor_returnsNullForBlank() {
    var extractor = new NumericExtractor();

    assertNull(extractor.extract("   "));
  }

  @Test
  void numericExtractor_handlesPlainNumber() {
    var extractor = new NumericExtractor();

    assertEquals("100", extractor.extract("100"));
  }

  // ==================== ShutterSpeedExtractor ====================

  @Test
  void shutterSpeedExtractor_passesThroughFractionFormat() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("1/100 sec", extractor.extract("1/100 sec"));
  }

  @Test
  void shutterSpeedExtractor_passesThroughFractionWithoutSuffix() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("1/100", extractor.extract("1/100"));
  }

  @Test
  void shutterSpeedExtractor_convertsDecimalToFraction() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("1/100 sec", extractor.extract("0.01"));
  }

  @Test
  void shutterSpeedExtractor_convertsDecimalOneEightieth() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("1/80 sec", extractor.extract("0.0125"));
  }

  @Test
  void shutterSpeedExtractor_convertsDecimalOneFiveHundredth() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("1/500 sec", extractor.extract("0.002"));
  }

  @Test
  void shutterSpeedExtractor_handlesWholeSeconds() {
    var extractor = new ShutterSpeedExtractor();

    assertEquals("2 sec", extractor.extract("2.0"));
    assertEquals("30 sec", extractor.extract("30"));
  }

  @Test
  void shutterSpeedExtractor_returnsNullForNull() {
    var extractor = new ShutterSpeedExtractor();

    assertNull(extractor.extract(null));
  }

  @Test
  void shutterSpeedExtractor_returnsNullForEmpty() {
    var extractor = new ShutterSpeedExtractor();

    assertNull(extractor.extract(""));
  }

  @Test
  void shutterSpeedExtractor_returnsNullForBlank() {
    var extractor = new ShutterSpeedExtractor();

    assertNull(extractor.extract("   "));
  }

  @Test
  void shutterSpeedExtractor_usedByShutterSpeedField() {
    var extractor = MetadataField.SHUTTER_SPEED.getExtractor();

    assertTrue(extractor instanceof ShutterSpeedExtractor);
  }

  // ==================== BooleanExtractor ====================

  @Test
  void booleanExtractor_returnsfalseForNull() {
    var extractor = new BooleanExtractor(value -> value.contains("yes"));

    assertEquals("false", extractor.extract(null));
  }

  @Test
  void booleanExtractor_returnsFalseForEmpty() {
    var extractor = new BooleanExtractor(value -> value.contains("yes"));

    assertEquals("false", extractor.extract(""));
  }

  @Test
  void booleanExtractor_returnsFalseForBlank() {
    var extractor = new BooleanExtractor(value -> value.contains("yes"));

    assertEquals("false", extractor.extract("   "));
  }

  @Test
  void booleanExtractor_returnsTrueWhenPredicateMatches() {
    var extractor = new BooleanExtractor(value -> value.contains("Monochrome"));

    assertEquals("true", extractor.extract("Monochrome, Landscape"));
  }

  @Test
  void booleanExtractor_returnsFalseWhenPredicateDoesNotMatch() {
    var extractor = new BooleanExtractor(value -> value.contains("Monochrome"));

    assertEquals("false", extractor.extract("Color, Landscape"));
  }

  @Test
  void booleanExtractor_blackAndWhiteFieldMatchesMonochrome() {
    var extractor = MetadataField.BLACK_AND_WHITE.getExtractor();

    assertEquals("true", extractor.extract("Monochrome"));
  }

  @Test
  void booleanExtractor_blackAndWhiteFieldMatchesBlackAndWhite() {
    var extractor = MetadataField.BLACK_AND_WHITE.getExtractor();

    assertEquals("true", extractor.extract("BlackAndWhite"));
  }

  @Test
  void booleanExtractor_isFilmFieldMatchesCaseInsensitive() {
    var extractor = MetadataField.IS_FILM.getExtractor();

    assertEquals("true", extractor.extract("Film"));
    assertEquals("true", extractor.extract("FILM"));
    assertEquals("true", extractor.extract("film"));
  }

  @Test
  void booleanExtractor_isFilmFieldReturnsFalseForDigital() {
    var extractor = MetadataField.IS_FILM.getExtractor();

    assertEquals("false", extractor.extract("Digital"));
  }
}
