package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ImageService {

    Map<String, String> getImageMetadata(MultipartFile image);

    Map<String, String> postImages(MultipartFile image, String type);

    ImageModel getImageById(Long imageId);

    @Transactional(readOnly = true)
    List<ImageModel> getAllImagesByCatalog(String catalogTitle);

    List<ImageModel> updateImages(ImageModel imageModel);

    List<ImageModel> searchByData(ImageSearchModel searchParams);

    List<ImageModel> postImagesForCatalog(List<MultipartFile> images, String catalogTitle) throws IOException;

}