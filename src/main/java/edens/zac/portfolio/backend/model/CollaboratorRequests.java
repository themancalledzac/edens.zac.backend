package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.types.DisplayMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Narrow request records for the collaborator tier (/api/edit). The permitted field set is a TYPE,
 * not a filter: a field added to CollectionRequests.Update stays invisible to collaborators unless
 * someone deliberately adds it here too -- the compiler enforces the boundary. The mapping
 * regression test (CollaboratorRequestsTest) pins every denied field to null.
 */
public final class CollaboratorRequests {

  private CollaboratorRequests() {}

  /** Collaborator-editable subset of {@link CollectionRequests.Update}. */
  public record CollaboratorUpdate(
      @NotNull Long id,
      @Size(min = 3, max = 100) String title,
      @Size(max = 500) String description,
      @JsonFormat(pattern = "yyyy-MM-dd") LocalDate collectionDate,
      Boolean clearCollectionDate,
      @Valid CollectionRequests.LocationUpdate locations,
      CollectionRequests.TagUpdate tags,
      Long coverImageId,
      @Min(0) @Max(5) Integer rating,
      DisplayMode displayMode,
      @Min(1) Integer rowsWide,
      @Min(1) Integer contentPerPage) {

    /**
     * Widen into the admin update DTO with every denied field null: slug, visibility,
     * collectionEndDate, clearCollectionEndDate, isClient, isBlog, people, collections, siblings,
     * parents. Positional over the 22-arg canonical constructor.
     */
    public CollectionRequests.Update toUpdate() {
      return new CollectionRequests.Update(
          id,
          null, // isClient
          null, // isBlog
          title,
          null, // slug
          description,
          locations,
          collectionDate,
          null, // collectionEndDate
          clearCollectionDate,
          null, // clearCollectionEndDate
          null, // visibility
          rating,
          displayMode,
          contentPerPage,
          rowsWide,
          coverImageId,
          tags,
          null, // people
          null, // collections
          null, // siblings
          null); // parents
    }
  }

  /**
   * Collaborator-editable subset of image fields, plus per-collection visibility. The four
   * canonical fields (title/caption/alt/rating) live on content_image and reach EVERY collection
   * the image appears in; {@code visible} writes only the (collection, image) join row.
   */
  public record CollaboratorImageUpdate(
      @NotNull Long id,
      String title,
      String caption,
      String alt,
      @Min(0) @Max(5) Integer rating,
      Boolean visible) {

    /** True when any canonical content_image field is present. */
    public boolean hasCanonicalEdit() {
      return title != null || caption != null || alt != null || rating != null;
    }

    /** Map the canonical fields onto the admin image-update type (visible handled separately). */
    public ContentImageUpdateRequest toImageUpdate() {
      return ContentImageUpdateRequest.builder()
          .id(id)
          .title(title)
          .caption(caption)
          .alt(alt)
          .rating(rating)
          .build();
    }
  }
}
