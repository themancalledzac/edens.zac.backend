package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.model.CollectionRequests.UpdateResponse;
import edens.zac.portfolio.backend.model.SaveAsCollectionRequest;
import edens.zac.portfolio.backend.services.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for tag operations.
 *
 * <p>Delegates all business logic to {@link TagService}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/tags")
public class TagAdminController {

  private final TagService tagService;

  @PostMapping("/{id}/save-as-collection")
  public ResponseEntity<UpdateResponse> saveAsCollection(
      @PathVariable Long id, @RequestBody(required = false) SaveAsCollectionRequest request) {
    return ResponseEntity.ok(tagService.convertTagToCollection(id, request));
  }
}
