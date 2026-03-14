package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;

/**
 * Sealed interface for all content block types. Pattern matching on subtypes is exhaustive.
 *
 * <p>Jackson polymorphism is driven by the {@code contentType} property. Spring Boot 3.4 / Jackson
 * 2.15+ support sealed interfaces and record canonical constructors without extra configuration.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "contentType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ContentModels.Image.class, name = "IMAGE"),
  @JsonSubTypes.Type(value = ContentModels.Text.class, name = "TEXT"),
  @JsonSubTypes.Type(value = ContentModels.Gif.class, name = "GIF"),
  @JsonSubTypes.Type(value = ContentModels.Collection.class, name = "COLLECTION")
})
public sealed interface ContentModel
    permits ContentModels.Image, ContentModels.Text, ContentModels.Gif, ContentModels.Collection {

  /** Content-table primary key. Same across all types; use for reordering. */
  Long id();

  /** Discriminator used by Jackson and frontend rendering logic. */
  ContentType contentType();

  /** Human-readable title for the content block. */
  String title();

  /**
   * Description text. Populated for COLLECTION content from the referenced collection; null for
   * IMAGE / TEXT / GIF.
   */
  String description();

  /**
   * Preview URL. For IMAGE this is the web-quality image URL; for COLLECTION this is the cover
   * image URL; null for TEXT.
   */
  String imageUrl();

  /**
   * Position of this content within its parent collection. Null when content is fetched outside a
   * collection context (e.g., admin image list).
   */
  Integer orderIndex();

  /** Visibility flag within the parent collection. Null outside a collection context. */
  Boolean visible();

  /** When the content entity was created. */
  LocalDateTime createdAt();

  /** When the content entity was last updated. */
  LocalDateTime updatedAt();
}
