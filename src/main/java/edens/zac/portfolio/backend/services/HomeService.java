package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HomeService {

    @Transactional(readOnly = true)
    List<HomeCardModel> getHomePage(int maxPriority);

    @Transactional
    void upsertHomeCardForCollection(CollectionEntity collection,
                                     boolean enabled,
                                     Integer priority,
                                     String text);

    @Transactional
    void syncHomeCardOnCollectionUpdate(CollectionEntity collection);
}
