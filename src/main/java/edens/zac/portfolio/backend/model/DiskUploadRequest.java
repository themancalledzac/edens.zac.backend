package edens.zac.portfolio.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for the file-path-only disk upload endpoints. Lightroom exports JPEGs to disk, then
 * sends paths for backend to process in background.
 *
 * <p>Shared by two endpoints: the collection-scoped {@code
 * /content/images/{collectionId}/from-disk} and the tag-first {@code /content/images/ingest} (which
 * derives a date-based BLOG collection per capture day and takes no collectionId). Both flows
 * consume the per-file {@code tags} and {@code locations} (resolved-or-created by name and attached
 * per image); {@code captureDate} is used only by the ingest flow for day-bucketing. All three are
 * optional/nullable so older from-disk callers are unaffected.
 */
public record DiskUploadRequest(
    @NotEmpty(message = "files list must not be empty") @Valid List<FileEntry> files,
    List<Long> locationIds) {

  /**
   * A single file pair: the exported JPEG path, optional RAW source path, and optional name-based
   * metadata. Both paths must be absolute paths on the local filesystem. People/tags/locations come
   * from Lightroom's keyword hierarchy ({@code People/*} -> people, {@code Location/*} ->
   * locations, everything else -> tags). {@code captureDate} is the photo's capture day in {@code
   * yyyy-MM-dd} form; when absent the server falls back to reading EXIF from the file on disk
   * (ingest flow only).
   */
  public record FileEntry(
      @NotBlank(message = "jpegPath must not be blank") String jpegPath,
      String rawPath,
      List<String> people,
      List<String> tags,
      List<String> locations,
      String captureDate) {

    /**
     * Backwards-compatible constructor for callers predating the tag-first ingest fields (existing
     * {@code /from-disk} callers and tests). Delegates with {@code tags}, {@code locations}, and
     * {@code captureDate} set to null.
     */
    public FileEntry(String jpegPath, String rawPath, List<String> people) {
      this(jpegPath, rawPath, people, null, null, null);
    }
  }
}
