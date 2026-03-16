package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Response DTO for image upload operations that may partially succeed. Contains both successfully
 * created images and per-file failure details.
 */
public record ImageUploadResult(
    List<ContentModels.Image> successful, List<FileError> failed, List<SkippedFile> skipped) {

  public record FileError(String filename, String error) {}

  public record SkippedFile(String filename, String reason) {}
}
