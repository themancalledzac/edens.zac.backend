package edens.zac.portfolio.backend.services;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPProperty;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extracts EXIF and XMP metadata from image files. Pure computation — no Spring bean dependencies,
 * no database or S3 access.
 */
@Component
@Slf4j
public class ImageMetadataExtractor {

  // Lightroom hierarchical subject XMP namespace
  private static final String NS_LIGHTROOM = "http://ns.adobe.com/lightroom/1.0/";

  // Metadata map keys for the two keyword-derived flags. Lightroom writes them as ordinary
  // keywords in dc:subject rather than as their own XMP properties, so they are read off the
  // keyword list rather than through the ImageMetadata field table.
  static final String FIELD_BLACK_AND_WHITE = "blackAndWhite";
  static final String FIELD_IS_FILM = "isFilm";

  private static final Set<String> BLACK_AND_WHITE_KEYWORDS =
      Set.of("monochrome", "blackandwhite", "black-and-white");
  private static final Set<String> FILM_KEYWORDS = Set.of("film");

  // Keywords that set a flag instead of becoming a tag. Derived from the two sets above so a
  // keyword can never set a flag and also survive as a tag.
  private static final Set<String> FILTERED_KEYWORDS =
      Stream.concat(BLACK_AND_WHITE_KEYWORDS.stream(), FILM_KEYWORDS.stream())
          .collect(Collectors.toUnmodifiableSet());

  /** Default values for image metadata fields. */
  public static final class DEFAULT {
    public static final String AUTHOR = "Zechariah Edens";
  }

  /** Tags and people extracted from image XMP keywords. */
  public record ExtractedKeywords(List<String> tags, List<String> people) {
    public static final ExtractedKeywords EMPTY = new ExtractedKeywords(List.of(), List.of());
  }

  /** Result of metadata extraction: technical fields plus keyword-based tags/people. */
  public record MetadataExtractionResult(
      Map<String, String> metadata, List<String> extractedTags, List<String> extractedPeople) {}

  /**
   * Extract all EXIF and XMP metadata from an image file.
   *
   * @param file The image file to extract metadata from
   * @return MetadataExtractionResult with technical metadata map plus extracted tags/people
   * @throws IOException If there's an error reading the file
   */
  public MetadataExtractionResult extractImageMetadata(MultipartFile file) throws IOException {
    String filename = file.getOriginalFilename();
    MetadataExtractionResult result;
    try (InputStream inputStream = file.getInputStream()) {
      result = extractFromStream(inputStream, filename);
    }

    if (!result.metadata().containsKey("imageWidth")
        || !result.metadata().containsKey("imageHeight")) {
      ensureDimensions(file, result.metadata());
    }

    return result;
  }

  /**
   * Extract all EXIF and XMP metadata from an image file on disk.
   *
   * @param filePath Path to the image file
   * @return MetadataExtractionResult with technical metadata map plus extracted tags/people
   * @throws IOException If there's an error reading the file
   */
  public MetadataExtractionResult extractImageMetadata(Path filePath) throws IOException {
    String filename = filePath.getFileName().toString();
    MetadataExtractionResult result;
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      result = extractFromStream(inputStream, filename);
    }

    if (!result.metadata().containsKey("imageWidth")
        || !result.metadata().containsKey("imageHeight")) {
      ensureDimensionsFromPath(filePath, result.metadata());
    }

    return result;
  }

  /**
   * Read EXIF directories first, then XMP, so an EXIF value wins and XMP only fills the gaps.
   * Keywords come from the first XMP directory yielding a non-empty result; later ones are skipped.
   */
  private MetadataExtractionResult extractFromStream(InputStream inputStream, String filename) {
    Map<String, String> metadata = new HashMap<>();
    ExtractedKeywords keywords = ExtractedKeywords.EMPTY;

    try {
      Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

      for (Directory directory : imageMetadata.getDirectories()) {
        for (Tag tag : directory.getTags()) {
          extractFromExifTag(tag, metadata);
        }
      }

      for (XmpDirectory xmpDirectory : imageMetadata.getDirectoriesOfType(XmpDirectory.class)) {
        extractFromXmpDirectory(xmpDirectory, metadata);

        if (keywords.tags().isEmpty() && keywords.people().isEmpty()) {
          try {
            XMPMeta xmpMeta = xmpDirectory.getXMPMeta();
            if (xmpMeta != null) {
              keywords = extractTagsAndPeopleFromXmp(xmpMeta, metadata);
            }
          } catch (Exception e) {
            log.warn("Failed to extract keywords from XMP for {}: {}", filename, e.getMessage());
          }
        }
      }

      log.trace(
          "Extracted metadata: {} fields, rating: {}, tags: {}, people: {}",
          metadata.size(),
          metadata.getOrDefault("rating", "NULL"),
          keywords.tags().size(),
          keywords.people().size());

    } catch (Exception e) {
      log.error("Failed to extract full metadata for {}: {}", filename, e.getMessage(), e);
    }

    return new MetadataExtractionResult(metadata, keywords.tags(), keywords.people());
  }

  /**
   * Extract metadata from a single EXIF tag using the ImageMetadata enum configuration. First write
   * wins.
   *
   * @param tag The EXIF tag to process
   * @param metadata The metadata map to populate
   */
  private void extractFromExifTag(Tag tag, Map<String, String> metadata) {
    String tagName = tag.getTagName();
    String description = tag.getDescription();

    if (description == null || description.isEmpty()) {
      return;
    }

    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      if (field.getExifTags().matches(tagName)) {
        if (!metadata.containsKey(field.getFieldName())) {
          String extractedValue = field.getExtractor().extract(description);
          if (extractedValue != null) {
            metadata.put(field.getFieldName(), extractedValue);
          }
        }
      }
    }
  }

  /**
   * Extract metadata from XMP directory using the ImageMetadata enum configuration. Each field's
   * (namespace, propertyName) pairs are tried in priority order; the first value wins.
   *
   * @param xmpDirectory The XMP directory to process
   * @param metadata The metadata map to populate
   */
  private void extractFromXmpDirectory(XmpDirectory xmpDirectory, Map<String, String> metadata) {
    XMPMeta xmpMeta = xmpDirectory.getXMPMeta();

    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      ImageMetadata.XmpProperty xmpProperty = field.getXmpProperty();

      if (!xmpProperty.hasProperties()) {
        continue;
      }

      for (ImageMetadata.XmpProperty.NamespaceProp entry : xmpProperty.getEntries()) {
        try {
          XMPProperty prop = xmpMeta.getProperty(entry.namespace(), entry.propertyName());

          if (prop != null && prop.getValue() != null) {
            if (!metadata.containsKey(field.getFieldName())) {
              String extractedValue = field.getExtractor().extract(prop.getValue());
              if (extractedValue != null) {
                metadata.put(field.getFieldName(), extractedValue);
                break; // Found value, stop trying further fallbacks
              }
            }
          }
        } catch (XMPException e) {
          log.trace(
              "XMP extraction failed for {}/{} (code {}): {}",
              entry.namespace(),
              entry.propertyName(),
              e.getErrorCode(),
              e.getMessage());
        }
      }
    }
  }

  /**
   * Extract all items from an XMP array property (bag or sequence).
   *
   * @param xmpMeta The XMP metadata object
   * @param namespace The XMP namespace URI
   * @param propertyName The array property name
   * @return List of string values, empty list if property is absent or on error
   */
  private List<String> extractXmpArrayItems(
      XMPMeta xmpMeta, String namespace, String propertyName) {
    List<String> items = new ArrayList<>();
    try {
      int count = xmpMeta.countArrayItems(namespace, propertyName);
      for (int i = 1; i <= count; i++) {
        XMPProperty item = xmpMeta.getArrayItem(namespace, propertyName, i);
        if (item != null && item.getValue() != null && !item.getValue().isBlank()) {
          items.add(item.getValue().trim());
        }
      }
    } catch (XMPException e) {
      log.trace(
          "XMP array extraction failed for {}/{}: {}", namespace, propertyName, e.getMessage());
    }
    return items;
  }

  /**
   * Extract tags and people from XMP keyword arrays. Uses lr:hierarchicalSubject to distinguish
   * people (under "People" parent) from tags. Falls back to dc:subject (flat keywords, all become
   * tags) if hierarchical subjects are not present.
   *
   * <p>Non-people entries keep only their leaf segment, so "Weather|sunset" yields "sunset". Tags
   * matching a person's name are then dropped: Lightroom emits a person both as "People|Name" and
   * as a standalone keyword, so without the filter the name returns as both.
   *
   * @param xmpMeta The XMP metadata object
   * @return ExtractedKeywords with separated tag and people name lists
   */
  private ExtractedKeywords extractTagsAndPeopleFromXmp(
      XMPMeta xmpMeta, Map<String, String> metadata) {
    List<String> hierarchicalSubjects =
        extractXmpArrayItems(xmpMeta, NS_LIGHTROOM, "hierarchicalSubject");

    if (!hierarchicalSubjects.isEmpty()) {
      List<String> tags = new ArrayList<>();
      List<String> people = new ArrayList<>();

      for (String subject : hierarchicalSubjects) {
        if (subject.toLowerCase().startsWith("people|")) {
          String personName = subject.substring("people|".length()).trim();
          if (!personName.isEmpty()) {
            people.add(personName);
          }
        } else {
          String leaf =
              subject.contains("|")
                  ? subject.substring(subject.lastIndexOf('|') + 1).trim()
                  : subject.trim();
          if (leaf.isEmpty()) {
            continue;
          }
          recordKeywordFlags(leaf, metadata);
          if (!FILTERED_KEYWORDS.contains(leaf.toLowerCase())) {
            tags.add(leaf);
          }
        }
      }

      if (!people.isEmpty()) {
        Set<String> peopleNamesLower = new HashSet<>();
        for (String name : people) {
          peopleNamesLower.add(name.toLowerCase());
        }
        tags.removeIf(tag -> peopleNamesLower.contains(tag.toLowerCase()));
      }

      return new ExtractedKeywords(tags, people);
    }

    List<String> dcSubjects = extractXmpArrayItems(xmpMeta, XMPConst.NS_DC, "subject");

    List<String> tags = new ArrayList<>();
    for (String subject : dcSubjects) {
      if (subject.isBlank()) {
        continue;
      }
      String keyword = subject.trim();
      recordKeywordFlags(keyword, metadata);
      if (!FILTERED_KEYWORDS.contains(keyword.toLowerCase())) {
        tags.add(keyword);
      }
    }

    return new ExtractedKeywords(tags, List.of());
  }

  /**
   * Set the blackAndWhite / isFilm flags from a single keyword.
   *
   * <p>These were previously configured as {@code xmp:subject} properties in the {@link
   * ImageMetadata} field table, which never matched: keywords live in {@code dc:subject}, and an
   * array property does not come back from {@code getProperty}. The flags therefore never fired,
   * while {@link #FILTERED_KEYWORDS} stripped the same keywords out of the tag list -- so the
   * signal was dropped entirely.
   *
   * <p>Matching is exact against the keyword sets rather than a substring test, so a keyword sets a
   * flag only when it is also the keyword being filtered out of the tags. "Film Noir" stays a tag;
   * "film" becomes the flag.
   */
  private void recordKeywordFlags(String keyword, Map<String, String> metadata) {
    String lower = keyword.toLowerCase();
    if (BLACK_AND_WHITE_KEYWORDS.contains(lower)) {
      metadata.put(FIELD_BLACK_AND_WHITE, "true");
    } else if (FILM_KEYWORDS.contains(lower)) {
      metadata.put(FIELD_IS_FILM, "true");
    }
  }

  /**
   * Ensure dimensions are present in metadata, reading them from the image header if needed.
   *
   * @param file The image file
   * @param metadata The metadata map to populate
   */
  void ensureDimensions(MultipartFile file, Map<String, String> metadata) {
    try (InputStream is = file.getInputStream()) {
      putDimensionsFromHeader(is, metadata);
    } catch (IOException e) {
      log.warn("Failed to read image dimensions from upload: {}", e.getMessage());
    }
  }

  /**
   * Ensure dimensions are present in metadata, reading them from the image header if needed.
   *
   * @param filePath Path to the image file
   * @param metadata The metadata map to populate
   */
  void ensureDimensionsFromPath(Path filePath, Map<String, String> metadata) {
    try (InputStream is = Files.newInputStream(filePath)) {
      putDimensionsFromHeader(is, metadata);
    } catch (IOException e) {
      log.warn("Failed to read image dimensions from path: {}", e.getMessage());
    }
  }

  /**
   * Read width and height out of the image header and put them in the metadata map.
   *
   * <p>Uses an ImageReader rather than ImageIO.read, which decodes every pixel. The upload pipeline
   * decodes the same file again right after metadata extraction, so a full decode here meant
   * decoding each image twice. The header read gives the same width and height.
   *
   * @param is Stream positioned at the start of the image
   * @param metadata The metadata map to populate
   * @throws IOException If the header cannot be read
   */
  private void putDimensionsFromHeader(InputStream is, Map<String, String> metadata)
      throws IOException {
    try (ImageInputStream imageStream = ImageIO.createImageInputStream(is)) {
      if (imageStream == null) {
        log.warn("No image input stream available, cannot read dimensions");
        return;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
      if (!readers.hasNext()) {
        log.warn("No image reader available, cannot read dimensions");
        return;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(imageStream, true, true);
        metadata.put("imageWidth", String.valueOf(reader.getWidth(0)));
        metadata.put("imageHeight", String.valueOf(reader.getHeight(0)));
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * Parse year and month from an EXIF or XMP date string. Splitting on {@code [: T-]} reads them as
   * the first two numeric runs of either EXIF "2024:05:15 14:30:00" or ISO-8601
   * "2024-05-15T14:30:00", so the two formats need not be told apart.
   *
   * @param createDate The capture date string from EXIF/XMP metadata
   * @param modifyDate The modify date string (Lightroom export date), used as fallback
   * @return int[] {year, month} or current date as last resort
   */
  public int[] parseImageDate(String createDate, String modifyDate) {
    if (createDate != null && !createDate.isEmpty()) {
      try {
        String[] parts = createDate.split("[: T-]");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
      } catch (Exception e) {
        log.warn("Failed to parse capture date '{}', trying modify date", createDate);
      }
    }
    if (modifyDate != null && !modifyDate.isEmpty()) {
      try {
        String[] parts = modifyDate.split("[: T-]");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
      } catch (Exception e) {
        log.warn("Failed to parse modify date '{}', using current date", modifyDate);
      }
    }
    log.warn("No valid date for S3 path, using current date");
    LocalDate now = LocalDate.now();
    return new int[] {now.getYear(), now.getMonthValue()};
  }

  /**
   * Parse an EXIF or XMP date string to a LocalDateTime.
   *
   * <p>Handles multiple date formats:
   *
   * <ul>
   *   <li>EXIF: "2020:09:27 08:42:51"
   *   <li>ISO with time: "2020-09-27T08:42:51"
   *   <li>ISO with timezone offset: "2020-09-27T08:42:51-07:00"
   *   <li>Date only (EXIF): "2020:09:27" -> midnight
   *   <li>Date only (ISO): "2020-09-27" -> midnight
   * </ul>
   *
   * <p>The two families are told apart by the fifth character, ":" versus "-", and EXIF is
   * normalized to ISO before parsing.
   *
   * @param createDate The date string from EXIF or XMP metadata
   * @return The parsed LocalDateTime, or null if parsing fails
   */
  public LocalDateTime parseExifDateToLocalDateTime(String createDate) {
    if (createDate == null || createDate.trim().isEmpty()) {
      return null;
    }

    String trimmed = createDate.trim();

    boolean isExifFormat = trimmed.length() > 4 && trimmed.charAt(4) == ':';

    try {
      if (isExifFormat) {
        String normalized = trimmed.replaceFirst(":", "-").replaceFirst(":", "-").replace(" ", "T");
        if (normalized.length() == 10) {
          return LocalDate.parse(normalized).atStartOfDay();
        }
        return LocalDateTime.parse(normalized);
      }

      if (trimmed.length() == 10) {
        return LocalDate.parse(trimmed).atStartOfDay();
      }

      if (trimmed.length() > 19 && (trimmed.contains("+") || trimmed.lastIndexOf('-') > 10)) {
        return OffsetDateTime.parse(trimmed).toLocalDateTime();
      }

      return LocalDateTime.parse(trimmed);
    } catch (DateTimeParseException e) {
      log.warn(
          "Failed to parse date '{}' to LocalDateTime: {}, date will be null",
          createDate,
          e.getMessage());
      return null;
    }
  }

  /**
   * Parse a string to an Integer, returning a default value if parsing fails. Characters other than
   * digits and a minus sign are stripped first, so "ISO 400" reads as 400.
   *
   * @param value The string value to parse
   * @param defaultValue The default value to return if parsing fails
   * @return The parsed integer or default value
   */
  public Integer parseIntegerOrDefault(String value, Integer defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      String cleaned = value.replaceAll("[^0-9-]", "");
      return Integer.parseInt(cleaned);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Parse a string to a Boolean. The default is returned only when the value is null or blank.
   *
   * <p>A non-blank value always parses, so there is no "parsing failed" case and the default is
   * never a fallback for an unrecognized value. "true" in any case and "1" are true; every other
   * non-blank value is false.
   *
   * <p>That is what the callers want. Both read a flag out of the extracted metadata map, where the
   * only value ever written is "true", and both pass false as the default. An unrecognized value
   * means the flag was not set, so false is the right answer rather than the default.
   *
   * @param value The string value to parse
   * @param defaultValue The value to return when value is null or blank
   * @return true for "true" or "1", false for any other non-blank value, defaultValue when blank
   */
  public Boolean parseBooleanOrDefault(String value, Boolean defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return Boolean.parseBoolean(trimmed) || trimmed.equals("1");
  }
}
