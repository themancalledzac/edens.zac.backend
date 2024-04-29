package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.CatalogModalDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CatalogService {



    @Transactional(readOnly = true)
    List<CatalogModel> getMainPageCatalogList();

    String createCatalog(CatalogModalDTO catalog);
}
