package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Probably update 'read' endpoints to just '/api/collections'
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class CollectionControllerProd {

    private final CollectionService collectionService;

    /**
     * Get all collections with basic info (paginated)
     *
     * @param page Page number (0-based)
     * @param size Page size
     * @return ResponseEntity with paginated collections
     */
    @GetMapping
    public ResponseEntity<?> getAllCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            // Normalize pagination parameters: 0-based pages; negative coerced to 0.
            if (page < 0) {
                page = 0;
            }
            if (size <= 0) {
                size = DefaultValues.default_collection_per_page;
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<CollectionModel> collections = collectionService.getAllCollections(pageable);

            return ResponseEntity.ok(collections);
        } catch (Exception e) {
            log.error("Error getting all collections: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve collections: " + e.getMessage());
        }
    }

    /**
     * Get collection by slug with paginated content
     * TODO: Need to work on a 'admin' vs 'regular' endpoint here, where:
     *  - regular would check if 'hasAccess' is true. if true, we would simply return a response body of 'no access'
     *  - admin would simply return all regardless.
     *  - Curious if we can just extend this, or otherwise duplicate it, while keeping 'local' dev development truly local only
     *  - Might be easiest to simply have a CollectionControllerDev endpoint to save time
     * @param slug Collection slug
     * @param page Page number (0-based)
     * @param size Page size
     * @return ResponseEntity with collection and paginated content
     */
    @GetMapping("/{slug}")
    public ResponseEntity<?> getCollectionBySlug(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            // Normalize pagination parameters: pages are 0-based; coerce negatives to 0 (first page)
            if (page < 0) {
                page = 0;
            }
            if (size <= 0) {
                size = DefaultValues.default_content_per_page;
            }

            CollectionModel collection = collectionService.getCollectionWithPagination(slug, page, size);
            return ResponseEntity.ok(collection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", slug);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with slug: " + slug + " not found");
        } catch (Exception e) {
            log.error("Error getting collection {}: {}", slug, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve collection: " + e.getMessage());
        }
    }

    /**
     * Get visible collections by type ordered by collection date (newest first)
     *
     * @param type Collection type
     * @return ResponseEntity with list of visible collections of the specified type as HomeCardModel objects
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getCollectionsByType(@PathVariable String type) {
        try {
            // Convert string type to enum
            CollectionType collectionType;
            try {
                collectionType = CollectionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Invalid collection type: " + type);
            }

            // Get visible collections ordered by date (newest first)
            List<HomeCardModel> collections = collectionService.findVisibleByTypeOrderByDate(collectionType);

            return ResponseEntity.ok(collections);
        } catch (Exception e) {
            log.error("Error getting collections by type {}: {}", type, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve collections: " + e.getMessage());
        }
    }

    /**
     * Validate client gallery access with password
     *
     * @param slug            Collection slug
     * @param passwordRequest Request body containing password
     * @return ResponseEntity with access status
     */
    @PostMapping("/{slug}/access")
    public ResponseEntity<?> validateClientGalleryAccess(
            @PathVariable String slug,
            @RequestBody Map<String, String> passwordRequest) {
        try {
            String password = passwordRequest.get("password");

            if (password == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Password is required");
            }

            boolean hasAccess = collectionService.validateClientGalleryAccess(slug, password);

            Map<String, Object> response = new HashMap<>();
            response.put("hasAccess", hasAccess);

            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", slug);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with slug: " + slug + " not found");
        } catch (Exception e) {
            log.error("Error validating access for collection {}: {}", slug, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to validate access: " + e.getMessage());
        }
    }
}
