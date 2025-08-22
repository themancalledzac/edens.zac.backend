package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HomeService {

    @Transactional(readOnly = true)
    List<HomeCardModel> getHomePage();

    @Transactional(readOnly = true)
    List<HomeCardModel> getHomePage(int maxPriority);

    @Transactional
    void createHomeCardFromCatalog(CatalogEntity catalog);

    @Transactional
    void updateHomeCard(CatalogEntity catalog);

    @Transactional
    void upsertHomeCardForCollection(ContentCollectionEntity collection,
                                     boolean enabled,
                                     Integer priority,
                                     String text,
                                     String coverImageUrl);

    @Transactional
    void syncHomeCardOnCollectionUpdate(ContentCollectionEntity collection);
}
