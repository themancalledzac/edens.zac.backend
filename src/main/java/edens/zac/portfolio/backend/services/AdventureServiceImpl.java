package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.AdventureEntity;
import edens.zac.portfolio.backend.model.AdventureModalDTO;
import edens.zac.portfolio.backend.model.AdventureModel;
import edens.zac.portfolio.backend.repository.AdventureRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AdventureServiceImpl implements AdventureService {

    private final ImageRepository imageRepository;
    private final AdventureRepository adventureRepository;

    public AdventureServiceImpl(ImageRepository imageRepository, AdventureRepository adventureRepository) {
        this.imageRepository = imageRepository;
        this.adventureRepository = adventureRepository;
    }

    @Override
    @Transactional
    public String createAdventure(AdventureModalDTO adventure) {
        AdventureEntity adventureEntity = AdventureEntity.builder()
                .name(adventure.getName())
                .build();
        AdventureEntity savedAdventure = adventureRepository.save(adventureEntity);
        return savedAdventure.getName();
    };

    @Override
    @Transactional
    public List<AdventureModel> getMainPageAdventureList() {

        // TODO:
        //  1. static list of adventures, FOR NOW
        //  2. Long term solution needed to have a 'saved' list of front page sections
        //  3. Would need to have a 'sections' idea instead, where we have a list of sections, BASED on adventures available
        //  4. FOR NOW, let's add a 'main=true/main=false' boolean DB column.
        //  5. Get Adventure where
//        adventureRepository.
        return null;
    }
}
