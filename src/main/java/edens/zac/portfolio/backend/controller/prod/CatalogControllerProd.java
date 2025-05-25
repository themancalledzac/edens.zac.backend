package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/read/catalog")
public class CatalogControllerProd {

    private final CatalogService catalogService;

    // TODO: Investigate if we need to 'paginate' the images, if they are over like 20 images, aka, if we have 200 images in a call, it can take a WHILE for the frontend to load.
    //  - This would be 2 things, 2 api getMapping calls, 'getCatalogMetadataBySlug' and 'getCatalogImagesBySlug', which would have a size of 20,
    //  - This would require the frontend to call both api calls, metadata first, and then basically call images 20 at a time
    //  - Only needed for catalogs with a LOT of images, is this even a real issue for us, other than, say, family or things?
    //  - Will need to investigate
    @GetMapping("bySlug/{slug}")
    public ResponseEntity<?> getCatalogWithImagesBySlug(
            @PathVariable String slug) {
        try {
            Optional<CatalogModel> catalog = catalogService.getCatalogBySlug(slug);
            if (catalog.isEmpty()) {
                log.warn("Catalog is null, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Catalog with slug: " + slug + " not found");
            }
            return ResponseEntity.ok(catalog);
        } catch (Exception e) {
            log.error("Error getting catalog {}: {}", slug, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve catalog: " + e.getMessage());
        }
    }

    @GetMapping("byId/{id}")
    public ResponseEntity<?> getCatalogById(@PathVariable Long id) {
        try {
            Optional<CatalogModel> catalog = catalogService.getCatalogById(id);
            if (catalog.isEmpty()) {
                log.warn("Catalog is null, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Catalog with id: " + id + " not found");
            }
            return ResponseEntity.ok(catalog);
        } catch (Exception e) {
            log.error("Error getting catalog {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve catalog: " + e.getMessage());
        }
    }

    @GetMapping("getAllCatalogs")
    public ResponseEntity<?> getAllCatalogs() {
        try {
            List<CatalogModel> catalogList = catalogService.getAllCatalogs();
            if (catalogList == null) {
                log.warn("No catalogs returned, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("No catalogs found");
            }
            return ResponseEntity.ok(catalogList);
        } catch (Exception e) {
            log.error("Error getting catalogs: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve catalogs: " + e.getMessage());
        }
    }
}
