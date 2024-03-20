package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.AdventureImagesDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ImageService {

    @Transactional(readOnly = true)
    List<AdventureImagesDTO> getAllImagesByAdventures(List<String> adventureTitles);

    Map<String, String> getImageMetadata(MultipartFile image);

    Map<String, String> postImage(MultipartFile image);

    ImageModel getImageById(Long imageId);

    @Transactional(readOnly = true)
    List<ImageModel> getAllImagesByAdventure(String adventureTitle);
}


//    List<PhotoCategoryPackage> getImagesByCategory(List<String> categories);
//    Image getImageByUuid(UUID uuid);

//    List<Image> getImageByCategory(String category);

//    Image saveImage(Image image);

//    List<Image> getImageByDate(String date);
