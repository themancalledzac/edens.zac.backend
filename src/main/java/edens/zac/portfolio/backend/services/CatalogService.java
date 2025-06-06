package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CatalogService {

    @Transactional
    CatalogModel createCatalogWithImages(CatalogCreateDTO catalogDTO, List<MultipartFile> images);

    Optional<CatalogModel> getCatalogBySlug(String slug);

    Optional<CatalogModel> getCatalogById(Long id);

    List<CatalogModel> getAllCatalogs();

    CatalogModel updateCatalog(CatalogUpdateDTO requestBody);
}
