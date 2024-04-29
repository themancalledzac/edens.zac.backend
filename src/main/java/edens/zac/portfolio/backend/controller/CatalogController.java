package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.CatalogModalDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.services.CatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/createCatalog")
    public String createCatalog(@RequestBody CatalogModalDTO catalog) {

        return catalogService.createCatalog(catalog);
    }

    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
    @RequestMapping(value = "/mainPageCatalogList")
    public List<CatalogModel> getMainPageCatalogList() {

        return catalogService.getMainPageCatalogList();
    }
}
