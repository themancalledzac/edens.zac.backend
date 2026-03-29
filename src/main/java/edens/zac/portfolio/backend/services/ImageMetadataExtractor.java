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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
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

  // Keywords to filter out (already handled by BLACK_AND_WHITE / IS_FILM metadata fields)
  private static final Set<String> FILTERED_KEYWORDS =
      Set.of("monochrome", "blackandwhite", "black-and-white", "film");

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
    Map<String, String> metadata = new HashMap<>();
    ExtractedKeywords keywords = ExtractedKeywords.EMPTY;

    try (InputStream inputStream = file.getInputStream()) {
      Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

      // Extract EXIF metadata for all defined fields
      for (Directory directory : imageMetadata.getDirectories()) {
        for (Tag tag : directory.getTags()) {
          extractFromExifTag(tag, metadata);
        }
      }

      // Extract XMP metadata for all defined fields + keywords/people
      for (XmpDirectory xmpDirectory : imageMetadata.getDirectoriesOfType(XmpDirectory.class)) {
        extractFromXmpDirectory(xmpDirectory, metadata);

        // Extract tags and people from XMP keyword arrays (stop after first non-empty result)
        if (keywords.tags().isEmpty() && keywords.people().isEmpty()) {
          try {
            XMPMeta xmpMeta = xmpDirectory.getXMPMeta();
            if (xmpMeta != null) {
              keywords = extractTagsAndPeopleFromXmp(xmpMeta);
            }
          } catch (Exception e) {
            log.warn(
                "Failed to extract keywords from XMP for {}: {}",
                file.getOriginalFilename(),
                e.getMessage());
          }
        }
      }

      if (!metadata.containsKey("createDate")) {
        log.warn("No capture date found in EXIF or XMP for file: {}", file.getOriginalFilename());
      }

      // Fallback: Get dimensions from BufferedImage if not found
      ensureDimensions(file, metadata);

      log.info("Extracted metadata: {} fields", metadata.size());
      log.info("Final rating value: {}", metadata.getOrDefault("rating", "NULL"));
      if (!keywords.tags().isEmpty() || !keywords.people().isEmpty()) {
        log.info(
            "Extracted {} tags and {} people from XMP keywords",
            keywords.tags().size(),
            keywords.people().size());
      }

    } catch (Exception e) {
      log.error(
          "Failed to extract full metadata for {}: {}",
          file.getOriginalFilename(),
          e.getMessage(),
          e);
      ensureDimensions(file, metadata);
    }

    return new MetadataExtractionResult(metadata, keywords.tags(), keywords.people());
  }

  /**
   * Extract metadata from a single EXIF tag using the ImageMetadata enum configuration.
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

    // Try each metadata field
    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      if (field.getExifTags().matches(tagName)) {
        // Only set if not already extracted
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
   * Extract metadata from XMP directory using the ImageMetadata enum configuration.
   *
   * @param xmpDirectory The XMP directory to process
   * @param metadata The metadata map to populate
   */
  private void extractFromXmpDirectory(XmpDirectory xmpDirectory, Map<String, String> metadata) {
    XMPMeta xmpMeta = xmpDirectory.getXMPMeta();

    // Try each metadata field
    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      ImageMetadata.XmpProperty xmpProperty = field.getXmpProperty();

      if (!xmpProperty.hasProperties()) {
        continue;
      }

      // Try each (namespace, propertyName) pair in priority order
      for (ImageMetadata.XmpProperty.NamespaceProp entry : xmpProperty.getEntries()) {
        try {
          XMPProperty prop = xmpMeta.getProperty(entry.namespace(), entry.propertyName());

          if (prop != null && prop.getValue() != null) {
            // Only set if not already extracted from EXIF
            if (!metadata.containsKey(field.getFieldName())) {
              String extractedValue = field.getExtractor().extract(prop.getValue());
              if (extractedValue != null) {
                metadata.put(field.getFieldName(), extractedValue);
                break; // Found value, stop trying further fallbacks
              }
            }
          }
        } catch (XMPException e) {
          log.debug(
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
      log.debug(
          "XMP array extraction failed for {}/{}: {}", namespace, propertyName, e.getMessage());
    }
    return items;
  }

  /**
   * Extract tags and people from XMP keyword arrays. Uses lr:hierarchicalSubject to distinguish
   * people (under "People" parent) from tags. Falls back to dc:subject (flat keywords, all become
   * tags) if hierarchical subjects are not present.
   *
   * @param xmpMeta The XMP metadata object
   * @return ExtractedKeywords with separated tag and people name lists
   */
  private ExtractedKeywords extractTagsAndPeopleFromXmp(XMPMeta xmpMeta) {
    // Try hierarchical subjects first (Lightroom writes these with category parents)
    List<String> hierarchicalSubjects =
        extractXmpArrayItems(xmpMeta, NS_LIGHTROOM, "hierarchicalSubject");

    if (!hierarchicalSubjects.isEmpty()) {
      List<String> tags = new ArrayList<>();
      List<String> people = new ArrayList<>();

      for (String subject : hierarchicalSubjects) {
        if (subject.toLowerCase().startsWith("people|")) {
          // "People|Jane Doe" → person "Jane Doe"
          String personName = subject.substring("people|".length()).trim();
          if (!personName.isEmpty()) {
            people.add(personName);
          }
        } else {
          // "Weather|sunset" → tag "sunset" (leaf segment)
          String leaf =
              subject.contains("|")
                  ? subject.substring(subject.lastIndexOf('|') + 1).trim()
                  : subject.trim();
          if (!leaf.isEmpty() && !FILTERED_KEYWORDS.contains(leaf.toLowerCase())) {
            tags.add(leaf);
          }
        }
      }

      return new ExtractedKeywords(tags, people);
    }

    // Fallback: flat dc:subject — all become tags, no people distinction
    List<String> dcSubjects = extractXmpArrayItems(xmpMeta, XMPConst.NS_DC, "subject");

    List<String> tags =
        dcSubjects.stream()
            .filter(s -> !s.isBlank())
            .map(String::trim)
            .filter(s -> !FILTERED_KEYWORDS.contains(s.toLowerCase()))
            .toList();

    return new ExtractedKeywords(tags, List.of());
  }

  /**
   * Ensure dimensions are present in metadata, reading from BufferedImage if needed.
   *
   * @param file The image file
   * @param metadata The metadata map to populate
   */
  private void ensureDimensions(MultipartFile file, Map<String, String> metadata) {
    if (!metadata.containsKey("imageWidth") || !metadata.containsKey("imageHeight")) {
      try (InputStream is = file.getInputStream()) {
        BufferedImage img = ImageIO.read(is);
        if (img != null) {
          metadata.put("imageWidth", String.valueOf(img.getWidth()));
          metadata.put("imageHeight", String.valueOf(img.getHeight()));
        }
      } catch (IOException e) {
        log.warn("Failed to read image dimensions from BufferedImage: {}", e.getMessage());
      }
    }
  }

  /**
   * Parse year and month from EXIF date string. EXIF date format is typically "2024:05:15
   * 14:30:00".
   *
   * @param createDate The capture date string from EXIF/XMP metadata
   * @param modifyDate The modify date string (Lightroom export date), used as fallback
   * @return int[] {year, month} or current date as last resort
   */
  public int[] parseImageDate(String createDate, String modifyDate) {
    if (createDate != null && !createDate.isEmpty()) {
      try {
        // EXIF format: "2024:05:15 14:30:00" or ISO-8601: "2024-05-15T14:30:00"
        String[] parts = createDate.split("[: T-]");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
      } catch (Exception e) {
        log.warn("Failed to parse capture date '{}', trying modify date", createDate);
      }
    }
    if (modifyDate != null && !modifyDate.isEmpty()) {
      try {
        String[] parts = modifyDate.split("[: T-]");
        log.info("Using modify date for S3 path: {}/{}", parts[0], parts[1]);
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
   * Parse an EXIF date string to a LocalDateTime.
   *
   * @param createDate The EXIF date string (e.g., "2026:01:26 17:48:38")
   * @return The parsed LocalDateTime, or null if parsing fails
   */
  public LocalDateTime parseExifDateToLocalDateTime(String createDate) {
    if (createDate == null || createDate.trim().isEmpty()) {
      return null;
    }
    try {
      // EXIF format: "2026:01:26 17:48:38" -> convert to "2026-01-26T17:48:38"
      // Replace first two colons (in date part) with dashes, then space with T
      String normalized =
          createDate.replaceFirst(":", "-").replaceFirst(":", "-").replace(" ", "T");
      return LocalDateTime.parse(normalized);
    } catch (Exception e) {
      log.warn("Failed to parse EXIF date '{}' to LocalDateTime, date will be null", createDate);
      return null;
    }
  }

  /**
   * Parse a string to an Integer, returning a default value if parsing fails.
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
      // Remove any non-numeric characters except minus sign
      String cleaned = value.replaceAll("[^0-9-]", "");
      return Integer.parseInt(cleaned);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Parse a string to a Boolean, returning a default value if parsing fails.
   *
   * @param value The string value to parse
   * @param defaultValue The default value to return if parsing fails
   * @return The parsed boolean or default value
   */
  public Boolean parseBooleanOrDefault(String value, Boolean defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value) || value.equals("1");
  }
}
