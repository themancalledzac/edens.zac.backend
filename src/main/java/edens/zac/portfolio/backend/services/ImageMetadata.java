package edens.zac.portfolio.backend.services;

import com.adobe.internal.xmp.XMPConst;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;

/**
 * Central configuration for image metadata extraction. Defines all metadata
 * fields we extract from
 * images, along with their sources and extraction strategies.
 */
public class ImageMetadata {

  /**
   * Enum defining all metadata fields that can be extracted from images. Each
   * field knows: - Its
   * field name in our system - Which EXIF tags to check - Which XMP properties to
   * check - How to
   * extract/transform the value
   */
  @Getter
  public enum MetadataField {
    IMAGE_WIDTH(
        "imageWidth",
        ExifTags.of("Image Width", "Exif Image Width"),
        XmpProperty.none(),
        new NumericExtractor()),

    IMAGE_HEIGHT(
        "imageHeight",
        ExifTags.of("Image Height", "Exif Image Height"),
        XmpProperty.none(),
        new NumericExtractor()),

    ISO(
        "iso",
        ExifTags.of("ISO Speed Ratings", "ISO"),
        XmpProperty.of(XMPConst.NS_EXIF, "ISOSpeedRatings"),
        new NumericExtractor()),

    RATING(
        "rating",
        ExifTags.of("Rating", "XMP Rating"),
        XmpProperty.of(XMPConst.NS_XMP, "Rating"),
        new NumericExtractor()),

    F_STOP(
        "fStop",
        ExifTags.of("F-Number"),
        XmpProperty.of(XMPConst.NS_EXIF, "FNumber"),
        new SimpleStringExtractor()),

    SHUTTER_SPEED(
        "shutterSpeed",
        ExifTags.of("Exposure Time", "Shutter Speed Value"),
        XmpProperty.of(XMPConst.NS_EXIF, "ExposureTime"),
        new SimpleStringExtractor()),

    LENS(
        "lens",
        ExifTags.of("Lens Model", "Lens"),
        XmpProperty.of(XMPConst.NS_EXIF_AUX, "Lens"),
        new SimpleStringExtractor()),

    CAMERA(
        "camera",
        ExifTags.of("Model", "Camera Model Name"),
        XmpProperty.of(XMPConst.NS_TIFF, "Model"),
        new SimpleStringExtractor()),

    FOCAL_LENGTH(
        "focalLength",
        ExifTags.of("Focal Length"),
        XmpProperty.of(XMPConst.NS_EXIF, "FocalLength"),
        new SimpleStringExtractor()),

    AUTHOR(
        "author",
        ExifTags.of("Artist", "Author"),
        XmpProperty.of(XMPConst.NS_DC, "creator"),
        new SimpleStringExtractor()),

    CREATE_DATE(
        "createDate",
        ExifTags.none(),
        XmpProperty.of(XMPConst.NS_EXIF, "DateTimeOriginal"),
        new SimpleStringExtractor()),

    OFFSET_TIME(
        "offsetTime",
        ExifTags.of("Offset Time", "Offset Time Original", "Offset Time Digitized"),
        XmpProperty.none(),
        new SimpleStringExtractor()),

    BODY_SERIAL_NUMBER(
        "bodySerialNumber",
        ExifTags.of("Body Serial Number"),
        XmpProperty.of(XMPConst.NS_EXIF_AUX, "SerialNumber"),
        new SimpleStringExtractor()),

    LENS_SERIAL_NUMBER(
        "lensSerialNumber",
        ExifTags.of("Lens Serial Number"),
        XmpProperty.of(XMPConst.NS_EXIF_AUX, "LensSerialNumber"),
        new SimpleStringExtractor()),

    BLACK_AND_WHITE(
        "blackAndWhite",
        ExifTags.none(),
        XmpProperty.of(XMPConst.NS_XMP, "subject"),
        new BooleanExtractor(
            value -> value.contains("Monochrome") || value.contains("BlackAndWhite"))),

    IS_FILM(
        "isFilm",
        ExifTags.none(),
        XmpProperty.of(XMPConst.NS_XMP, "subject"),
        new BooleanExtractor(value -> value.toLowerCase().contains("film")));

    private final String fieldName;
    private final ExifTags exifTags;
    private final XmpProperty xmpProperty;
    private final ValueExtractor extractor;

    MetadataField(
        String fieldName, ExifTags exifTags, XmpProperty xmpProperty, ValueExtractor extractor) {
      this.fieldName = fieldName;
      this.exifTags = exifTags;
      this.xmpProperty = xmpProperty;
      this.extractor = extractor;
    }
  }

  // ==================== Helper Classes ====================

  /**
   * Wrapper for EXIF tag names. Some metadata can be found under multiple EXIF
   * tag names (e.g.,
   * "ISO" or "ISO Speed Ratings").
   */
  @Getter
  public static class ExifTags {
    private final List<String> tagNames;

    private ExifTags(List<String> tagNames) {
      this.tagNames = tagNames;
    }

    public static ExifTags of(String... tagNames) {
      return new ExifTags(Arrays.asList(tagNames));
    }

    public static ExifTags none() {
      return new ExifTags(Collections.emptyList());
    }

    public boolean matches(String tagName) {
      return tagNames.stream().anyMatch(name -> name.equalsIgnoreCase(tagName));
    }

    public boolean hasTagNames() {
      return !tagNames.isEmpty();
    }
  }

  /**
   * Wrapper for XMP namespace and property names. XMP metadata is organized by
   * namespace (e.g.,
   * NS_EXIF) and property name (e.g., "ISOSpeedRatings").
   */
  @Getter
  public static class XmpProperty {
    private final String namespace;
    private final List<String> propertyNames;

    private XmpProperty(String namespace, List<String> propertyNames) {
      this.namespace = namespace;
      this.propertyNames = propertyNames;
    }

    public static XmpProperty of(String namespace, String... propertyNames) {
      return new XmpProperty(namespace, Arrays.asList(propertyNames));
    }

    public static XmpProperty none() {
      return new XmpProperty(null, Collections.emptyList());
    }

    public boolean hasProperties() {
      return namespace != null && !propertyNames.isEmpty();
    }
  }

  // ==================== Extraction Strategies ====================

  /**
   * Interface for value extraction strategies. Different metadata types require
   * different
   * extraction/transformation logic.
   */
  public interface ValueExtractor {
    String extract(String rawValue);
  }

  /** Simple pass-through extractor. Returns the value as-is after trimming. */
  public static class SimpleStringExtractor implements ValueExtractor {
    @Override
    public String extract(String rawValue) {
      if (rawValue == null || rawValue.trim().isEmpty()) {
        return null;
      }
      return rawValue.trim();
    }
  }

  /**
   * Numeric extractor. Strips non-numeric characters (except decimal point) and
   * returns the numeric
   * value. Useful for fields like "ISO 400" -> "400" or "f/2.8" -> "2.8"
   */
  public static class NumericExtractor implements ValueExtractor {
    @Override
    public String extract(String rawValue) {
      if (rawValue == null || rawValue.trim().isEmpty()) {
        return null;
      }

      // Strip everything except digits and decimal point
      String numeric = rawValue.replaceAll("[^0-9.]", "");

      if (numeric.isEmpty()) {
        return null;
      }

      return numeric;
    }
  }

  /**
   * Boolean extractor with custom predicate. Tests the value against a predicate
   * and returns "true"
   * or "false".
   */
  public static class BooleanExtractor implements ValueExtractor {
    private final Predicate<String> predicate;

    public BooleanExtractor(Predicate<String> predicate) {
      this.predicate = predicate;
    }

    @Override
    public String extract(String rawValue) {
      if (rawValue == null || rawValue.trim().isEmpty()) {
        return "false";
      }

      return predicate.test(rawValue) ? "true" : "false";
    }
  }
}
