package edens.zac.portfolio.backend.services;

import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Diagnostic test for confirming exact Java metadata-extractor tag names for date fields.
 *
 * <p>Run on-demand against a real image file:
 *
 * <pre>
 * mvn test -Dtest=ImageMetadataExtractionTest -Dimage.path="/path/to/photo.jpg"
 * </pre>
 *
 * Enable by removing @Disabled or passing -Dimage.path when running.
 */
class ImageMetadataExtractionTest {

  @Test
  void dumpAllMetadata() throws Exception {
    String imagePath = System.getProperty("image.path");
    if (imagePath == null || imagePath.isBlank()) {
      System.out.println("No image.path system property set. Skipping.");
      return;
    }

    File file = new File(imagePath);
    System.out.println("=== Reading: " + file.getAbsolutePath() + " ===\n");

    Metadata metadata = ImageMetadataReader.readMetadata(file);

    // --- EXIF directories ---
    System.out.println("========== EXIF DIRECTORIES ==========");
    for (Directory directory : metadata.getDirectories()) {
      if (directory instanceof XmpDirectory) {
        continue; // handled separately below
      }
      System.out.println("\n[Directory: " + directory.getName() + "]");
      for (Tag tag : directory.getTags()) {
        String tagName = tag.getTagName();
        String description = tag.getDescription();
        String line = "  " + tagName + " = " + description;
        // Highlight date-related tags
        if (tagName.toLowerCase().contains("date")
            || tagName.toLowerCase().contains("time")
            || tagName.toLowerCase().contains("create")) {
          line = "*** DATE *** " + line;
        }
        System.out.println(line);
      }
    }

    // --- XMP directory ---
    System.out.println("\n========== XMP PROPERTIES ==========");
    for (XmpDirectory xmpDirectory : metadata.getDirectoriesOfType(XmpDirectory.class)) {
      XMPMeta xmpMeta = xmpDirectory.getXMPMeta();
      if (xmpMeta == null) {
        System.out.println("  (no XMP meta)");
        continue;
      }
      XMPIterator iterator = xmpMeta.iterator();
      while (iterator.hasNext()) {
        XMPPropertyInfo propInfo = (XMPPropertyInfo) iterator.next();
        if (propInfo.getPath() == null || propInfo.getPath().isBlank()) {
          continue;
        }
        String ns = propInfo.getNamespace();
        String path = propInfo.getPath();
        String value = propInfo.getValue();
        String line = "  [" + ns + "] " + path + " = " + value;
        // Highlight date-related properties
        if (path.toLowerCase().contains("date")
            || path.toLowerCase().contains("time")
            || path.toLowerCase().contains("create")) {
          line = "*** DATE *** " + line;
        }
        System.out.println(line);
      }
    }

    System.out.println("\n=== Done ===");
  }
}
