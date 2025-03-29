package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.ImageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class CatalogProcessingUtil {

    private final ImageProcessingUtil imageProcessingUtil;

    @Autowired
    public CatalogProcessingUtil(ImageProcessingUtil imageProcessingUtil) {
        this.imageProcessingUtil = imageProcessingUtil;
    }

    /**
     * Converts a Catalog Entity to a Catalog Model
     *
     * @param catalogEntity CatalogEntity
     * @return CatalogModel
     */
    CatalogModel convertToCatalogModel(CatalogEntity catalogEntity) {
        // Convert the image entities to image models
        List<ImageModel> images = catalogEntity.getImages().stream()
                .filter(Objects::nonNull)
                .map(imageProcessingUtil::convertImageEntityToImageModel)
                .toList();

        return CatalogModel.builder()
                .id(catalogEntity.getId())
                .title(catalogEntity.getTitle())
                .location(catalogEntity.getLocation())
                .priority(catalogEntity.getPriority())
                .coverImageUrl(catalogEntity.getCoverImageUrl())
                .people(catalogEntity.getPeople())
                .tags(catalogEntity.getTags())
                .slug(catalogEntity.getSlug())
                .date(catalogEntity.getDate())
                .images(images)
                .build();
    }
}
