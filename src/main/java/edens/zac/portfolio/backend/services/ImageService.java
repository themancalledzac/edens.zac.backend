package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.Image;
import edens.zac.portfolio.backend.model.PhotoCategoryPackage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ImageService {


    Image getImageByUuid(UUID uuid);

    List<Image> getImageByCategory(String category);

    Image saveImage(Image image);

    List<Image> getImageByDate(String date);

    Map<String, String> getImageMetadata(MultipartFile image);

    List<PhotoCategoryPackage> getImagesByCategory(List<String> categories);

    Map<String, String> postImage(MultipartFile image);
}


