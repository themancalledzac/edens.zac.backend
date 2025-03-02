package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.model.ImageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class BlogProcessingUtil {

    private final ImageProcessingUtil imageProcessingUtil;

    @Autowired
    public BlogProcessingUtil(ImageProcessingUtil imageProcessingUtil) {
        this.imageProcessingUtil = imageProcessingUtil;
    }

    /**
     * Converts a Blog Entity to a Blog Model
     *
     * @param blogEntity BlogEntity
     * @return BlogModel
     */
    BlogModel convertToBlogModel(BlogEntity blogEntity) {

        // Convert the image entities to image models
        List<ImageModel> images = blogEntity.getImages().stream()
                .map(imageProcessingUtil::convertImageEntityToImageModel)
                .toList();

        BlogModel blogModel = new BlogModel();
        blogModel.setId(blogEntity.getId());
        blogModel.setTitle(blogEntity.getTitle());
        blogModel.setDate(blogEntity.getDate());
        blogModel.setLocation(blogEntity.getLocation());
        blogModel.setParagraph(blogEntity.getParagraph());
        blogModel.setAuthor(blogEntity.getAuthor());
        blogModel.setCoverImageUrl(blogEntity.getCoverImageUrl());
        blogModel.setSlug(blogEntity.getSlug());
        blogModel.setImages(images);
        return blogModel;
    }
}
