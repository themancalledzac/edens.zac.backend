package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CatalogService {

    @Transactional
    CatalogModel createCatalogWithImages(CatalogCreateDTO catalogDTO, List<MultipartFile> images);

    CatalogModel getCatalogBySlug(String slug);

    CatalogModel getCatalogById(Long id);

    List<CatalogModel> getAllCatalogs();
}
