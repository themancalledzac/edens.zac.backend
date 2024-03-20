package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.AdventureModalDTO;
import edens.zac.portfolio.backend.model.AdventureModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AdventureService {



    @Transactional(readOnly = true)
    List<AdventureModel> getMainPageAdventureList();

    String createAdventure(AdventureModalDTO adventure);
}
