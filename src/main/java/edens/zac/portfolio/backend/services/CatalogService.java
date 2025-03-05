package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CatalogService {

    @Transactional
    CatalogModel createCatalogWithImages(CatalogCreateDTO catalogDTO, List<MultipartFile> images);

    @Transactional(readOnly = true)
    List<CatalogModel> getMainPageCatalogList();

//    String createCatalog(CatalogModalDTO catalog);

    List<CatalogModel> catalogSearch(String search);

//    CatalogModel updateCatalog(CatalogModel catalog);

//    CatalogImagesDTO updateCatalogWithImages(CatalogImagesDTO catalogWithImages);

}
