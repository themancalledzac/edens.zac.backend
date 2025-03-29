package edens.zac.portfolio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping(value = "/uploadCatalogWithImages",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> uploadCatalogWithImages(
            @RequestPart("catalogDTO") String catalogDtoJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        try {
            // convert JSON string to a CatalogCreateDTO object
            ObjectMapper objectMapper = new ObjectMapper();
            CatalogCreateDTO catalogDTO = objectMapper.readValue(catalogDtoJson, CatalogCreateDTO.class);

            // Validate the catalog data
            if (catalogDTO.getTitle() == null || catalogDTO.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Catalog title is required");
            }

            // Create Catalog
            CatalogModel createCatalog = catalogService.createCatalogWithImages(catalogDTO, images);
            log.info("Successfully created catalog: {}", createCatalog.getId());

            return ResponseEntity.ok(createCatalog);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create catalog " + e.getMessage());
        }
    }

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


    // TODO:
    //  - Update no longer carries Images
    //  - Images are uploaded in real time BEFORE the catalog is updated ( on fail, we don't add to current, on success we do! )
    //  - Will need to return image objects(including urls) on success, this way we can add them
    //  -
    @PutMapping(value = "update/")
    public ResponseEntity<?> updateCatalog(
            @RequestBody(required = true) CatalogUpdateDTO requestBody) {

        try {
            if (requestBody.getTitle() == null || requestBody.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Catalog title is required");
            }

            CatalogModel updatedCatalog = catalogService.updateCatalog(requestBody);
            log.info("Successfully updated catalog: {}", updatedCatalog.getId());

            return ResponseEntity.ok(updatedCatalog);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update catalog " + e.getMessage());
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
