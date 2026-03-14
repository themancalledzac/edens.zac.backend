package edens.zac.portfolio.backend.services;

import com.adobe.internal.xmp.XMPConst;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;

/**
 * Central configuration for image metadata extraction. Defines all metadata fields we extract from
 * images, along with their sources and extraction strategies.
 */
public class ImageMetadata {

  /**
   * Enum defining all metadata fields that can be extracted from images. Each field knows: - Its
   * field name in our system - Which EXIF tags to check - Which XMP properties to check - How to
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

    /**
     * Image capture date from EXIF/XMP metadata. Uses the original capture date/time from when the
     * photo was taken, not the file modification date.
     *
     * <p>Priority order: NS_EXIF/DateTimeOriginal (standard EXIF), NS_EXIF/DateTimeDigitized
     * (fallback EXIF), NS_XMP/CreateDate (Lightroom digital exports), NS_PHOTOSHOP/DateCreated
     * (also written by Lightroom on export).
     */
    CREATE_DATE(
        "createDate",
        ExifTags.of(
            "Date/Time Original", "DateTimeOriginal", "Date/Time Digitized", "DateTimeDigitized"),
        XmpProperty.ofFallbacks(
            new XmpProperty.NamespaceProp(XMPConst.NS_EXIF, "DateTimeOriginal"),
            new XmpProperty.NamespaceProp(XMPConst.NS_EXIF, "DateTimeDigitized"),
            new XmpProperty.NamespaceProp(XMPConst.NS_XMP, "CreateDate"),
            new XmpProperty.NamespaceProp(XMPConst.NS_PHOTOSHOP, "DateCreated")),
        new SimpleStringExtractor()),

    MODIFY_DATE(
        "modifyDate",
        ExifTags.of("Date/Time", "DateTime"),
        XmpProperty.of(XMPConst.NS_XMP, "ModifyDate"),
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
   * Wrapper for EXIF tag names. Some metadata can be found under multiple EXIF tag names (e.g.,
   * "ISO" or "ISO Speed Ratings").
   */
  @Getter
  public static class ExifTags {
    private final List<String> tagNames;

    private ExifTags(List<String> tagNames) {
      this.tagNames = tagNames;
    }

    public static ExifTags of(String... tagNames) {
      return new ExifTags(List.of(tagNames));
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
   * Wrapper for XMP namespace/property pairs to try in priority order. Each entry is a (namespace,
   * propertyName) pair; the first one that yields a non-null value wins.
   */
  @Getter
  public static class XmpProperty {
    private final List<NamespaceProp> entries;

    /** A single XMP namespace + property name pair to look up. */
    public record NamespaceProp(String namespace, String propertyName) {}

    private XmpProperty(List<NamespaceProp> entries) {
      this.entries = entries;
    }

    /** Single namespace, one or more property names tried in order. */
    public static XmpProperty of(String namespace, String... propertyNames) {
      return new XmpProperty(
          Arrays.stream(propertyNames).map(p -> new NamespaceProp(namespace, p)).toList());
    }

    /** Multiple (namespace, propertyName) pairs tried in priority order. */
    public static XmpProperty ofFallbacks(NamespaceProp... pairs) {
      return new XmpProperty(List.of(pairs));
    }

    public static XmpProperty none() {
      return new XmpProperty(Collections.emptyList());
    }

    public boolean hasProperties() {
      return !entries.isEmpty();
    }
  }

  // ==================== Extraction Strategies ====================

  /**
   * Interface for value extraction strategies. Different metadata types require different
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
   * Numeric extractor. Strips non-numeric characters (except decimal point) and returns the numeric
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
   * Boolean extractor with custom predicate. Tests the value against a predicate and returns "true"
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
