package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Covers the dimension fallback and the value parsing helpers on {@link ImageMetadataExtractor}.
 *
 * <p>Keyword flags are covered separately in {@link ImageMetadataExtractorKeywordFlagTest}.
 */
class ImageMetadataExtractorTest {

  private final ImageMetadataExtractor extractor = new ImageMetadataExtractor();

  private static byte[] jpegBytes(int width, int height) throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
    return out.toByteArray();
  }

  @Nested
  class EnsureDimensions {

    @TempDir Path tempDir;

    @Test
    void ensureDimensionsFromPath_realJpeg_writesWidthAndHeight() throws Exception {
      Path jpeg = tempDir.resolve("dims.jpg");
      Files.write(jpeg, jpegBytes(120, 45));
      Map<String, String> metadata = new HashMap<>();

      extractor.ensureDimensionsFromPath(jpeg, metadata);

      assertThat(metadata).containsEntry("imageWidth", "120").containsEntry("imageHeight", "45");
    }

    @Test
    void ensureDimensionsFromPath_headerRead_matchesFullDecode() throws Exception {
      // The header read replaced an ImageIO.read of the whole file. Same numbers, no pixels.
      Path jpeg = tempDir.resolve("compare.jpg");
      Files.write(jpeg, jpegBytes(333, 211));
      Map<String, String> metadata = new HashMap<>();

      extractor.ensureDimensionsFromPath(jpeg, metadata);

      BufferedImage decoded = ImageIO.read(jpeg.toFile());
      assertThat(metadata.get("imageWidth")).isEqualTo(String.valueOf(decoded.getWidth()));
      assertThat(metadata.get("imageHeight")).isEqualTo(String.valueOf(decoded.getHeight()));
    }

    @Test
    void ensureDimensions_multipartFile_writesWidthAndHeight() throws Exception {
      var file = new MockMultipartFile("files", "upload.jpg", "image/jpeg", jpegBytes(64, 96));
      Map<String, String> metadata = new HashMap<>();

      extractor.ensureDimensions(file, metadata);

      assertThat(metadata).containsEntry("imageWidth", "64").containsEntry("imageHeight", "96");
    }

    @Test
    void ensureDimensions_bytesThatAreNotAnImage_leavesMetadataUntouched() {
      var file =
          new MockMultipartFile(
              "files", "broken.jpg", "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8));
      Map<String, String> metadata = new HashMap<>();

      extractor.ensureDimensions(file, metadata);

      assertThat(metadata).isEmpty();
    }
  }

  @Nested
  class ParseBooleanOrDefault {

    @Test
    void nullValue_returnsDefault() {
      assertThat(extractor.parseBooleanOrDefault(null, false)).isFalse();
      assertThat(extractor.parseBooleanOrDefault(null, true)).isTrue();
    }

    @Test
    void blankValue_returnsDefault() {
      assertThat(extractor.parseBooleanOrDefault("   ", true)).isTrue();
      assertThat(extractor.parseBooleanOrDefault("", true)).isTrue();
    }

    @Test
    void trueValue_returnsTrueRegardlessOfCase() {
      assertThat(extractor.parseBooleanOrDefault("true", false)).isTrue();
      assertThat(extractor.parseBooleanOrDefault("TRUE", false)).isTrue();
      assertThat(extractor.parseBooleanOrDefault("True", false)).isTrue();
    }

    @Test
    void oneValue_returnsTrue() {
      assertThat(extractor.parseBooleanOrDefault("1", false)).isTrue();
    }

    @Test
    void paddedValue_isTrimmedBeforeMatching() {
      assertThat(extractor.parseBooleanOrDefault(" true ", false)).isTrue();
      assertThat(extractor.parseBooleanOrDefault(" 1 ", false)).isTrue();
    }

    @Test
    void unrecognizedValue_returnsFalseNotTheDefault() {
      // The default covers a missing value, not an unrecognized one. A present value that is
      // neither "true" nor "1" means the flag was not set, so it reads as false.
      assertThat(extractor.parseBooleanOrDefault("maybe", true)).isFalse();
      assertThat(extractor.parseBooleanOrDefault("0", true)).isFalse();
      assertThat(extractor.parseBooleanOrDefault("false", true)).isFalse();
    }
  }
}
