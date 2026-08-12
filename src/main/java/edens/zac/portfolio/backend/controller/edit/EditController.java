package edens.zac.portfolio.backend.controller.edit;

import edens.zac.portfolio.backend.model.CollaboratorRequests;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collaborator-tier write surface. Every route carries {collectionId} -- required by
 * CollaboratorAccessInterceptor, which gates this surface at COLLABORATOR-or-above (global admins
 * outrank). Kept separate from /api/admin/** so the blanket admin rule stays intact: a new endpoint
 * added to AdminController is still admin-only by default. Exception handling is delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/edit")
class EditController {

  private final CollectionService collectionService;
  private final ContentService contentService;

  /** Atomic image reorder; recomputes sequential indices for all content in the collection. */
  @PostMapping("/collections/{collectionId}/reorder")
  public ResponseEntity<CollectionModel> reorderCollectionContent(
      @PathVariable Long collectionId, @RequestBody @Valid CollectionRequests.Reorder request) {
    log.debug(
        "Reordering content in collection {} with {} reorder operations",
        collectionId,
        request.reorders().size());
    CollectionModel updatedCollection = collectionService.reorderContent(collectionId, request);
    log.info("Reordered content in collection: {}", collectionId);
    return ResponseEntity.ok(updatedCollection);
  }

  /** Click-to-rate for the collection itself; 0-5, null clears. */
  @PatchMapping("/collections/{collectionId}/rating")
  ResponseEntity<Void> patchRating(
      @PathVariable Long collectionId, @Valid @RequestBody RatingPatch body) {
    collectionService.updateRating(collectionId, body.rating());
    return ResponseEntity.noContent().build();
  }

  /** Body for the rating patch. */
  public record RatingPatch(@Min(0) @Max(5) Integer rating) {}

  /**
   * Collaborator-scope collection update: the narrow DTO is widened onto the admin update path so
   * there stays exactly one implementation of the write. The path id is the authorized scope; a
   * mismatched body id is rejected before any work. A non-clearing {@code coverImageId} (0 or null
   * both mean "clear", per CollectionRequests.Update's javadoc) must also belong to this collection
   * -- otherwise a collaborator could point the cover at an image from a gallery they cannot see,
   * exposing its CDN URL.
   */
  @PutMapping("/collections/{collectionId}")
  public ResponseEntity<CollectionRequests.UpdateResponse> updateCollection(
      @PathVariable Long collectionId,
      @RequestBody @Valid CollaboratorRequests.CollaboratorUpdate body) {
    if (!collectionId.equals(body.id())) {
      throw new IllegalArgumentException(
          "Body id " + body.id() + " must match path collection id " + collectionId);
    }
    if (body.coverImageId() != null && body.coverImageId() != 0) {
      collectionService.requireImagesInCollection(collectionId, List.of(body.coverImageId()));
    }
    return ResponseEntity.ok(
        collectionService.updateContentWithMetadata(collectionId, body.toUpdate()));
  }

  /**
   * Collaborator-scope image edits. The guard runs first: every id must belong to the path
   * collection (403 otherwise, before any write). Canonical fields (title/caption/alt/rating) then
   * route through the single ContentService.updateImages implementation -- these reach every
   * collection the image appears in. The scoped {@code visible} flag writes only this collection's
   * join row.
   */
  @PatchMapping("/collections/{collectionId}/images")
  public ResponseEntity<Map<String, Object>> patchImages(
      @PathVariable Long collectionId,
      @RequestBody List<CollaboratorRequests.@Valid CollaboratorImageUpdate> updates) {
    if (updates == null || updates.isEmpty()) {
      throw new IllegalArgumentException("At least one image update is required");
    }
    List<Long> ids =
        updates.stream().map(CollaboratorRequests.CollaboratorImageUpdate::id).toList();
    collectionService.requireImagesInCollection(collectionId, ids);

    List<ContentImageUpdateRequest> canonical =
        updates.stream()
            .filter(CollaboratorRequests.CollaboratorImageUpdate::hasCanonicalEdit)
            .map(CollaboratorRequests.CollaboratorImageUpdate::toImageUpdate)
            .toList();
    Map<String, Object> response =
        canonical.isEmpty()
            ? new HashMap<>()
            : new HashMap<>(contentService.updateImages(canonical));

    int visibleUpdated = 0;
    for (CollaboratorRequests.CollaboratorImageUpdate update : updates) {
      if (update.visible() != null) {
        collectionService.updateImageVisibility(collectionId, update.id(), update.visible());
        visibleUpdated++;
      }
    }
    response.put("visibleUpdated", visibleUpdated);
    return ResponseEntity.ok(response);
  }
}
