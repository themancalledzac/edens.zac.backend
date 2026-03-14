package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sealed interface implementations for ContentModel. Each content type is a record containing all
 * base fields plus type-specific fields. Records are immutable; use withCollections() to rebuild an
 * Image with updated collections.
 */
public final class ContentModels {
  private ContentModels() {}

  /**
   * Image content block. Carries all EXIF metadata, equipment, tagging, and collection membership
   * data. Created by ContentProcessingUtil; collections populated by CollectionService.
   */
  public record Image(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      Integer orderIndex,
      Boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      Integer imageWidth,
      Integer imageHeight,
      Integer iso,
      String author,
      Integer rating,
      String fStop,
      Records.Lens lens,
      Boolean blackAndWhite,
      Boolean isFilm,
      String filmType,
      FilmFormat filmFormat,
      String shutterSpeed,
      Records.Camera camera,
      String focalLength,
      Records.Location location,
      String createDate,
      List<Records.Tag> tags,
      List<Records.Person> people,
      List<Records.ChildCollection> collections)
      implements ContentModel {

    /** Returns a new Image with the collections field replaced. */
    public Image withCollections(List<Records.ChildCollection> collections) {
      return new Image(
          id,
          contentType,
          title,
          description,
          imageUrl,
          orderIndex,
          visible,
          createdAt,
          updatedAt,
          imageWidth,
          imageHeight,
          iso,
          author,
          rating,
          fStop,
          lens,
          blackAndWhite,
          isFilm,
          filmType,
          filmFormat,
          shutterSpeed,
          camera,
          focalLength,
          location,
          createDate,
          tags,
          people,
          collections);
    }
  }

  /** Text content block (markdown, code, plain text, etc.). */
  public record Text(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      Integer orderIndex,
      Boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String textContent,
      String formatType)
      implements ContentModel {}

  /** Animated GIF content block. */
  public record Gif(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      Integer orderIndex,
      Boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String gifUrl,
      String thumbnailUrl,
      Integer width,
      Integer height,
      String author,
      String createDate,
      List<Records.Tag> tags)
      implements ContentModel {}

  /**
   * Collection reference content block. The {@code id} field is the content-table ID used for
   * reordering; {@code referencedCollectionId} navigates to the actual collection.
   */
  public record Collection(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      Integer orderIndex,
      Boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      Long referencedCollectionId,
      String slug,
      CollectionType collectionType,
      ContentModels.Image coverImage)
      implements ContentModel {}
}
