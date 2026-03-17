package edens.zac.portfolio.backend.services.validator;

import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validator for content creation operations (images, text). Centralizes validation logic for
 * content operations.
 */
@Component
@Slf4j
public class ContentValidator {

  /**
   * Validate that at least one file is provided for image creation.
   *
   * @param files The list of files to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateFiles(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("At least one file is required");
    }
  }

  /**
   * Validate that at least one image update is provided.
   *
   * @param updates The list of updates to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateImageUpdates(List<?> updates) {
    if (updates == null || updates.isEmpty()) {
      throw new IllegalArgumentException("At least one image update is required");
    }
  }

  /**
   * Validate that at least one image ID is provided for deletion.
   *
   * @param imageIds The list of image IDs to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateImageIds(List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      throw new IllegalArgumentException("At least one image ID is required");
    }
  }

  /**
   * Validate text content.
   *
   * @param textContent The text content to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateTextContent(String textContent) {
    if (textContent == null || textContent.trim().isEmpty()) {
      throw new IllegalArgumentException("Text content is required");
    }
  }

  private static final Set<String> VIDEO_MIME_TYPES = Set.of("video/mp4", "video/quicktime");

  private static final Set<String> ACCEPTED_GIF_MIME_TYPES =
      Set.of("image/gif", "video/mp4", "video/quicktime");

  /**
   * Validate that a GIF or MP4 file is provided.
   *
   * @param file The file to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateGifFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("GIF/MP4 file cannot be empty");
    }

    String contentType = file.getContentType();
    if (contentType == null || !ACCEPTED_GIF_MIME_TYPES.contains(contentType.toLowerCase())) {
      throw new IllegalArgumentException(
          "File type not accepted. Expected image/gif, video/mp4, or video/quicktime, got: "
              + contentType);
    }
  }

  /** Returns true if the file has a video MIME type (MP4 or QuickTime). */
  public boolean isMp4File(MultipartFile file) {
    String ct = file.getContentType();
    return ct != null && VIDEO_MIME_TYPES.contains(ct);
  }
}
