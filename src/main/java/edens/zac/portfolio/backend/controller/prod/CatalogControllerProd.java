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

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/read/catalog")
public class CatalogControllerProd {

    private final CatalogService catalogService;

    @GetMapping("bySlug/{slug}")
    public ResponseEntity<?> getCatalogWithImagesBySlug(
            @PathVariable String slug) {
        try {
            CatalogModel catalog = catalogService.getCatalogBySlug(slug);
            if (catalog == null) {
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
            CatalogModel catalog = catalogService.getCatalogById(id);
            if (catalog == null) {
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
