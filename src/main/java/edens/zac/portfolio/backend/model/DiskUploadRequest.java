package edens.zac.portfolio.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for the file-path-only disk upload endpoint. Lightroom exports JPEGs to disk, then
 * sends paths for backend to process in background.
 */
public record DiskUploadRequest(
    @NotEmpty(message = "files list must not be empty") @Valid List<FileEntry> files,
    List<Long> locationIds) {

  /**
   * A single file pair: the exported JPEG path, optional RAW source path, and optional people
   * names. Both paths must be absolute paths on the local filesystem. People names come from
   * Lightroom's keyword hierarchy (keywords under the "People" parent).
   */
  public record FileEntry(
      @NotBlank(message = "jpegPath must not be blank") String jpegPath,
      String rawPath,
      List<String> people) {}
}
