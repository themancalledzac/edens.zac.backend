package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.HomeCardEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class HomeProcessingUtil {

    public HomeCardModel convertModel(HomeCardEntity entity) {
        return HomeCardModel.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .cardType(entity.getCardType())
                .location(entity.getLocation())
                .date(entity.getDate())
                .priority(entity.getPriority())
                .coverImageUrl(entity.getCoverImageUrl())
                .slug(entity.getSlug())
                .text(entity.getText())
                .build();
    }

    public HomeCardEntity createHomeCardFromCatalog(CatalogEntity catalog) {
        return HomeCardEntity.builder()
                .title(catalog.getTitle())
                .cardType("catalog")
                .location(catalog.getLocation())
                .date(catalog.getDate() != null ? catalog.getDate().toString() : null)
                .priority(catalog.getPriority())
                .coverImageUrl(catalog.getCoverImageUrl())
                .text(null)
                .slug(catalog.getSlug())
                .referenceId(catalog.getId())
                .createdDate(LocalDateTime.now())
                .isActiveHomeCard(catalog.isHomeCard())
                .build();
    }
}
