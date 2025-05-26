package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HomeService {

    @Transactional(readOnly = true)
    List<HomeCardModel> getHomePage();

    @Transactional
    void createHomeCardFromCatalog(CatalogEntity catalog);

    @Transactional
    void updateHomeCard(CatalogEntity catalog);
}
