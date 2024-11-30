package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.CatalogImagesDTO;
import edens.zac.portfolio.backend.model.CatalogModalDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
    @PostMapping("/createCatalog")
    public String createCatalog(@RequestBody CatalogModalDTO catalog) {

        return catalogService.createCatalog(catalog);
    }

    //    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
    @RequestMapping(value = "/mainPageCatalogList")
    public List<CatalogModel> getMainPageCatalogList() {

        return catalogService.getMainPageCatalogList();
    }

    @GetMapping("/search/{search}")
    public List<CatalogModel> catalogSearch(@PathVariable("search") String search) {
        return catalogService.catalogSearch(search);
    }

    //  TODO:: Update Catalog - When creating a new catalog for an image, we relevant fields ( which, after success, will be added to the images, if already selected )
    @PutMapping(value = "/update")
    public CatalogModel updateCatalog(@RequestBody CatalogModel catalog) {

        return catalogService.updateCatalog(catalog);
    }

    // TODO: update CatalogImagesDTO to include all Catalog fields, or make a new one if we need that minimal
    @PutMapping(value= "/updateWithImages")
    public CatalogImagesDTO updateCatalogAndImages(@RequestBody CatalogImagesDTO catalogWithImages) {

        return catalogService.updateCatalogWithImages(catalogWithImages);
    }
}
