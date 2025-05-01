package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ImageService {

    Map<String, String> getImageMetadata(MultipartFile image);

    Map<String, String> postImages(MultipartFile image, String type);

    Optional<ImageModel> getImageById(Long imageId);

    @Transactional(readOnly = true)
    List<ImageModel> getAllImagesByCatalog(String catalogTitle);

    ImageEntity updateImage(ImageModel imageModel);

    List<ImageModel> searchByData(ImageSearchModel searchParams);

    List<ImageModel> postImagesForCatalog(List<MultipartFile> images, String catalogTitle) throws IOException;

}