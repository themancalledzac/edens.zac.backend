package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Response DTO for image upload operations that may partially succeed. Contains both successfully
 * created images and per-file failure details. The collectionId field is populated when a new
 * collection was created as part of the upload (create-collection endpoint).
 */
public record ImageUploadResult(
    Long collectionId,
    List<ContentModels.Image> successful,
    List<FileError> failed,
    List<SkippedFile> skipped) {

  /** Constructor without collectionId for regular uploads. */
  public ImageUploadResult(
      List<ContentModels.Image> successful, List<FileError> failed, List<SkippedFile> skipped) {
    this(null, successful, failed, skipped);
  }

  public record FileError(String filename, String error) {}

  public record SkippedFile(String filename, String reason) {}
}
