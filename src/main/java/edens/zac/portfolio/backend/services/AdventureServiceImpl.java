package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.AdventureEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.AdventureModalDTO;
import edens.zac.portfolio.backend.model.AdventureModel;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.AdventureRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        // TODO: Make optional number of additions later on, such as location, stravaLink, etc
        AdventureEntity adventureEntity = AdventureEntity.builder()
                .name(adventure.getName())
                .build();
        AdventureEntity savedAdventure = adventureRepository.save(adventureEntity);
        return savedAdventure.getName();
    }

    ;

    @Override
    @Transactional
    public List<AdventureModel> getMainPageAdventureList() {

        List<AdventureModalDTO> mainAdventures = adventureRepository.findMainAdventures();
        return mainAdventures.stream().map(this::adventureConvertToModel)
                .collect(Collectors.toList());
    }

    private AdventureModel adventureConvertToModel(AdventureModalDTO adventureEntity) {

        ImageModel adventureImageConverted = null;
        if (adventureEntity.getImageMainTitle() != null) {
            Optional<ImageEntity> adventureImageOpt = imageRepository.findByTitle(adventureEntity.getImageMainTitle());
            adventureImageConverted = adventureImageOpt.map(this::convertToModalImage)
                    .orElse(null);
        }
        return new AdventureModel(adventureEntity.getId(), adventureEntity.getName(), adventureImageConverted);

    }

    // This is a duplicate of ImageServiceImpl. It needs to be moved elsewhere
    private ImageModel convertToModalImage(ImageEntity image) {
        List<String> adventureNames = image.getAdventures().stream().map(AdventureEntity::getName).collect(Collectors.toList());

        ImageModel modalImage = new ImageModel(); //  is not public in 'edens.zac.portfolio.backend.model.ModalImage'. Cannot be accessed from outside package
        modalImage.setTitle(image.getTitle());
        modalImage.setImageWidth(image.getImageWidth());
        modalImage.setImageHeight(image.getImageHeight());
        modalImage.setIso(image.getIso());
        modalImage.setAuthor(image.getAuthor());
        modalImage.setRating(image.getRating());
        modalImage.setFStop(image.getFStop());
        modalImage.setLens(image.getLens());
        modalImage.setAdventure(adventureNames);
        modalImage.setBlackAndWhite(image.getBlackAndWhite());
        modalImage.setShutterSpeed(image.getShutterSpeed());
        modalImage.setRawFileName(image.getRawFileName());
        modalImage.setCamera(image.getCamera());
        modalImage.setFocalLength(image.getFocalLength());
        modalImage.setLocation(image.getLocation());
        modalImage.setImageUrlLarge(image.getImageUrlLarge());
        modalImage.setImageUrlSmall(image.getImageUrlSmall());
        modalImage.setImageUrlRaw(image.getImageUrlRaw());
        modalImage.setCreateDate(image.getCreateDate());
        modalImage.setUpdateDate(image.getUpdateDate());
        modalImage.setId(image.getId());
        return modalImage;
    }
}
