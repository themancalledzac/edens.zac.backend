package edens.zac.portfolio.backend.services;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ImageService {

    Map<String, String> getImageMetadata(MultipartFile image);

    Map<String, String> postImage(MultipartFile image);
}


//    List<PhotoCategoryPackage> getImagesByCategory(List<String> categories);
//    Image getImageByUuid(UUID uuid);

//    List<Image> getImageByCategory(String category);

//    Image saveImage(Image image);

//    List<Image> getImageByDate(String date);
