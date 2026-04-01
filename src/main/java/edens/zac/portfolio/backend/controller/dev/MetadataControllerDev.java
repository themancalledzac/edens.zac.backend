package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.MetadataService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for metadata management operations (dev environment only). Provides endpoints for
 * updating and deleting tags, people, and locations.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/metadata")
@Profile("dev")
class MetadataControllerDev {

  private final MetadataService metadataService;

  @PutMapping("/tags/{id}")
  ResponseEntity<Records.Tag> updateTag(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Tag updated = metadataService.updateTag(id, name);
    log.info("Updated tag: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/people/{id}")
  ResponseEntity<Records.Person> updatePerson(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Person updated = metadataService.updatePerson(id, name);
    log.info("Updated person: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/locations/{id}")
  ResponseEntity<Records.Location> updateLocation(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Location updated = metadataService.updateLocation(id, name);
    log.info("Updated location: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

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
