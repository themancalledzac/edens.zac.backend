package edens.zac.portfolio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
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
            // convert JSON string to a BlogCreateDTO object
            ObjectMapper objectMapper = new ObjectMapper();
            CatalogCreateDTO catalogDTO = objectMapper.readValue(catalogDtoJson, CatalogCreateDTO.class);

            // Validate the blog data
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

//    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
//    @PostMapping("/createCatalog")
//    public String createCatalog(@RequestBody CatalogModalDTO catalog) {
//
//        return catalogService.createCatalog(catalog);
//    }
//
//    //    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
//    @RequestMapping(value = "/mainPageCatalogList")
//    public List<CatalogModel> getMainPageCatalogList() {
//
//        return catalogService.getMainPageCatalogList();
//    }
//
//    @GetMapping("/search/{search}")
//    public List<CatalogModel> catalogSearch(@PathVariable("search") String search) {
//        return catalogService.catalogSearch(search);
//    }
//
//    //  TODO:: Update Catalog - When creating a new catalog for an image, we relevant fields ( which, after success, will be added to the images, if already selected )
//    @PutMapping(value = "/update")
//    public CatalogModel updateCatalog(@RequestBody CatalogModel catalog) {
//
//        return catalogService.updateCatalog(catalog);
//    }
//
//    // TODO: update CatalogImagesDTO to include all Catalog fields, or make a new one if we need that minimal
//    @PutMapping(value = "/updateWithImages")
//    public CatalogImagesDTO updateCatalogAndImages(@RequestBody CatalogImagesDTO catalogWithImages) {
//
//        return catalogService.updateCatalogWithImages(catalogWithImages);
//    }
}
