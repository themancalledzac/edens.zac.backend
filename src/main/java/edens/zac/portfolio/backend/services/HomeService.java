package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HomeService {

    @Transactional(readOnly = true)
    List<HomeCardModel> getHomePage();

    @Transactional
    HomeCardModel createHomeCardFromCatalog(CatalogEntity catalog, Integer priority);

    @Transactional
    HomeCardModel createHomeCardFromBlog(BlogEntity blog, Integer priority);

    @Transactional
    HomeCardModel createHomeCardFromPrevious(HomeCardModel homeCardModel);
}
