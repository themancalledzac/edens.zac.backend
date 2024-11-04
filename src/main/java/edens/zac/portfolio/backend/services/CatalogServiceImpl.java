package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogModalDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CatalogServiceImpl implements CatalogService {

    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;

    public CatalogServiceImpl(ImageRepository imageRepository, CatalogRepository catalogRepository) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
    }

    @Override
    @Transactional
    public String createCatalog(CatalogModalDTO catalog) {
        // TODO: Make optional number of additions later on, such as location, stravaLink, etc
        CatalogEntity catalogEntity = CatalogEntity.builder()
                .name(catalog.getName())
                .priority(catalog.getPriority())
                .mainCatalog(catalog.getMainCatalog())
                .build();
        CatalogEntity savedCatalog = catalogRepository.save(catalogEntity);
        return savedCatalog.getName();
    }

    ;

    @Override
    @Transactional
    public List<CatalogModel> getMainPageCatalogList() {

        List<CatalogModalDTO> mainCatalogs = catalogRepository.findMainCatalogs();
        return mainCatalogs.stream().map(this::catalogConvertToModel)
                .collect(Collectors.toList());
    }

    private CatalogModel catalogConvertToModel(CatalogModalDTO catalogEntity) {

        ImageModel catalogImageConverted = null;
        if (catalogEntity.getImageMainTitle() != null) {
            Optional<ImageEntity> catalogImageOpt = imageRepository.findByTitle(catalogEntity.getImageMainTitle());
            catalogImageConverted = catalogImageOpt.map(this::convertToModalImage)
                    .orElse(null);
        }
        return new CatalogModel(catalogEntity.getId(), catalogEntity.getName(), catalogImageConverted, catalogEntity.getPriority());

    }

    // This is a duplicate of ImageServiceImpl. It needs to be moved elsewhere
    private ImageModel convertToModalImage(ImageEntity image) {
        List<String> catalogNames = image.getCatalogs().stream().map(CatalogEntity::getName).collect(Collectors.toList());

        ImageModel modalImage = new ImageModel(); //  is not public in 'edens.zac.portfolio.backend.model.ModalImage'. Cannot be accessed from outside package
        modalImage.setTitle(image.getTitle());
        modalImage.setImageWidth(image.getImageWidth());
        modalImage.setImageHeight(image.getImageHeight());
        modalImage.setIso(image.getIso());
        modalImage.setAuthor(image.getAuthor());
        modalImage.setRating(image.getRating());
        modalImage.setFStop(image.getFStop());
        modalImage.setLens(image.getLens());
        modalImage.setCatalog(catalogNames);
        modalImage.setBlackAndWhite(image.getBlackAndWhite());
        modalImage.setShutterSpeed(image.getShutterSpeed());
        modalImage.setRawFileName(image.getRawFileName());
        modalImage.setCamera(image.getCamera());
        modalImage.setFocalLength(image.getFocalLength());
        modalImage.setLocation(image.getLocation());
        modalImage.setImageUrlWeb(image.getImageUrlWeb());
        modalImage.setImageUrlSmall(image.getImageUrlSmall());
        modalImage.setImageUrlRaw(image.getImageUrlRaw());
        modalImage.setCreateDate(image.getCreateDate());
        modalImage.setUpdateDate(image.getUpdateDate());
        modalImage.setId(image.getId());
        return modalImage;
    }
}
