//package edens.zac.portfolio.backend.services;
//
//import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
//import edens.zac.portfolio.backend.model.HomeCardModel;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//@Component
//@Slf4j
//public class HomeProcessingUtil {
//
//    public HomeCardModel convertModel(ContentCollectionHomeCardEntity entity) {
//        return HomeCardModel.builder()
//                .id(entity.getId())
//                .title(entity.getTitle())
//                .cardType(entity.getCardType())
//                .location(entity.getLocation())
//                .date(entity.getDate())
//                .priority(entity.getPriority())
//                .coverImageUrl(entity.getCoverImageUrl())
//                .slug(entity.getSlug())
//                .text(entity.getText())
//                .build();
//    }
//}
