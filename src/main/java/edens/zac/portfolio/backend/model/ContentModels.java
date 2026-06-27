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
   * data. Created by ContentModelConverter; collections populated by CollectionProcessingUtil.
   */
  public record Image(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String caption,
      String alt,
      String imageUrl,
      String imageUrlRaw,
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
      List<Records.Location> locations,
      LocalDateTime captureDate,
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
          caption,
          alt,
          imageUrl,
          imageUrlRaw,
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
          locations,
          captureDate,
          tags,
          people,
          collections);
    }

    /** Returns a new Image with {@code orderIndex} replaced (records are immutable). */
    public Image withOrderIndex(Integer orderIndex) {
      return new Image(
          id,
          contentType,
          title,
          description,
          caption,
          alt,
          imageUrl,
          imageUrlRaw,
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
          locations,
          captureDate,
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
      String gifUrlWeb,
      String thumbnailUrl,
      Integer width,
      Integer height,
      String author,
      String createDate,
      Integer rating,
      List<Records.Tag> tags,
      List<Records.Person> people,
      List<Records.Location> locations,
      List<Records.ChildCollection> collections)
      implements ContentModel {

    /** Returns a new Gif with the collections field replaced (records are immutable). */
    public Gif withCollections(List<Records.ChildCollection> collections) {
      return new Gif(
          id,
          contentType,
          title,
          description,
          imageUrl,
          orderIndex,
          visible,
          createdAt,
          updatedAt,
          gifUrl,
          gifUrlWeb,
          thumbnailUrl,
          width,
          height,
          author,
          createDate,
          rating,
          tags,
          people,
          locations,
          collections);
    }

    /** Returns a new Gif with {@code orderIndex} replaced (records are immutable). */
    public Gif withOrderIndex(Integer orderIndex) {
      return new Gif(
          id,
          contentType,
          title,
          description,
          imageUrl,
          orderIndex,
          visible,
          createdAt,
          updatedAt,
          gifUrl,
          gifUrlWeb,
          thumbnailUrl,
          width,
          height,
          author,
          createDate,
          rating,
          tags,
          people,
          locations,
          collections);
    }
  }

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
      implements ContentModel {

    /**
     * Build a child-reference block from a basic {@link CollectionModel} (a converted collection,
     * not a content row). Used by synthetic PARENT views (synthetic list slugs and the {@code
     * /user} page) where each child collection becomes a {@code COLLECTION} block pointing back at
     * itself.
     */
    public static Collection fromCollectionModel(CollectionModel c) {
      return new Collection(
          c.getId(),
          ContentType.COLLECTION,
          c.getTitle(),
          c.getDescription(),
          null,
          0,
          true,
          c.getCreatedAt(),
          c.getUpdatedAt(),
          c.getId(),
          c.getSlug(),
          c.getType(),
          c.getCoverImage());
    }

    /** Returns a new Collection with {@code orderIndex} replaced (records are immutable). */
    public Collection withOrderIndex(Integer orderIndex) {
      return new Collection(
          id,
          contentType,
          title,
          description,
          imageUrl,
          orderIndex,
          visible,
          createdAt,
          updatedAt,
          referencedCollectionId,
          slug,
          collectionType,
          coverImage);
    }
  }
}
