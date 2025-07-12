package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.services.ContentCollectionService;
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
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class ContentCollectionControllerProd {

    private final ContentCollectionService contentCollectionService;
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int DEFAULT_COLLECTION_PAGE_SIZE = 10;

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
            @RequestParam(defaultValue = "10") int size) {
        try {
            // Validate pagination parameters
            if (page < 0) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Page number cannot be negative");
            }

            if (size <= 0) {
                size = DEFAULT_COLLECTION_PAGE_SIZE;
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<ContentCollectionModel> collections = contentCollectionService.getAllCollections(pageable);

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
     *
     * @param slug Collection slug
     * @param page Page number (0-based)
     *             
     * @param size Page size
     * @return ResponseEntity with collection and paginated content
     */
    @GetMapping("/{slug}")
    public ResponseEntity<?> getCollectionBySlug(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            // Validate pagination parameters
            if (page < 0) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Page number cannot be negative");
            }

            if (size <= 0) {
                size = DEFAULT_PAGE_SIZE;
            }

            ContentCollectionModel collection = contentCollectionService.getCollectionWithPagination(slug, page, size);
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
     * Get collections by type (paginated)
     *
     * @param type Collection type
     * @param page Page number (0-based)
     * @param size Page size
     * @return ResponseEntity with paginated collections of the specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getCollectionsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // Validate pagination parameters
            if (page < 0) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Page number cannot be negative");
            }

            if (size <= 0) {
                size = DEFAULT_COLLECTION_PAGE_SIZE;
            }

            // Convert string type to enum
            CollectionType collectionType;
            try {
                collectionType = CollectionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Invalid collection type: " + type);
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<ContentCollectionModel> collections = contentCollectionService.findByType(collectionType, pageable);

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

            boolean hasAccess = contentCollectionService.validateClientGalleryAccess(slug, password);

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
