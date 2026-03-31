package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.services.MetadataService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for metadata delete operations (dev environment only). Provides endpoints for deleting
 * tags, people, and locations, cascading removal of associations from all content and collections.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/metadata")
@Profile("dev")
class MetadataControllerDev {

  private final MetadataService metadataService;

  @DeleteMapping("/tags/{id}")
  ResponseEntity<Map<String, Boolean>> deleteTag(@PathVariable Long id) {
    metadataService.deleteTag(id);
    log.info("Deleted tag: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @DeleteMapping("/people/{id}")
  ResponseEntity<Map<String, Boolean>> deletePerson(@PathVariable Long id) {
    metadataService.deletePerson(id);
    log.info("Deleted person: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @DeleteMapping("/locations/{id}")
  ResponseEntity<Map<String, Boolean>> deleteLocation(@PathVariable Long id) {
    metadataService.deleteLocation(id);
    log.info("Deleted location: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }
}
