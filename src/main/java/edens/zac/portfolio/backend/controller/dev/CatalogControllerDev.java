package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/write/catalog")
@Configuration
@Profile("dev")
public class CatalogControllerDev {

    private final CatalogService catalogService;

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
}
