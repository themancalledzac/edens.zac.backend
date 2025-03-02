package edens.zac.portfolio.backend.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.BlogCreateDTO;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.repository.BlogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class BlogServiceImpl implements BlogService {
    private final BlogRepository blogRepository;
    private final ImageRepository imageRepository;
    private final ImageProcessingUtil imageProcessingUtil;
    private final BlogProcessingUtil blogProcessingUtil;

    public BlogServiceImpl(
            BlogRepository blogRepository,
            ImageProcessingUtil imageProcessingUtil,
            ImageRepository imageRepository,
            BlogProcessingUtil blogProcessingUtil
    ) {
        this.blogRepository = blogRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.imageRepository = imageRepository;
        this.blogProcessingUtil = blogProcessingUtil;
    }

//    public BlogServiceImpl

    @Override
    @Transactional
    public List<BlogModel> getAllBlogs() {
        return null;
    }

    @Override
    public BlogModel createBlog(BlogCreateDTO blogDTO, List<MultipartFile> images) {

        // Part 1: Initialize image collection
        Set<ImageEntity> blogImages = new HashSet<>();

        // Part 2: Process existing images if IDs were provided
        if (blogDTO.getExistingImageIds() != null && !blogDTO.getExistingImageIds().isEmpty()) {
            log.info("Processing {} existing images", blogDTO.getExistingImageIds().size());
            for (Long imageId : blogDTO.getExistingImageIds()) {
                Optional<ImageEntity> existingImage = imageRepository.findById(imageId);
                existingImage.ifPresent(blogImages::add);
            }
        }

        // Part 3: Process and upload new images, if any
        if (images != null && !images.isEmpty()) {
            log.info("Processing {} new uploaded images", images.size());
            for (MultipartFile image : images) {
                try {
                    // Process Image
                    ImageEntity imageEntity = imageProcessingUtil.processAndSaveImage(image, "blog", blogDTO.getTitle());
                    blogImages.add(imageEntity);

                    // If this is the first image and no cover image was specified, use as cover
                    if (blogImages.size() > 1 && blogDTO.getCoverImageUrl() == null) {
                        // TODO: is 'ImageUrlWeb' actually what we want? are we setting web version? Where??
                        blogDTO.setCoverImageUrl(imageEntity.getImageUrlWeb());
                    }
                } catch (Exception e) {
                    log.error("Error processing image for blog: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                } finally {
                    // todo: why is this needed?
                    System.out.println("test");
                }
            }
        }

        // Part 4: Create Blog Entity
        BlogEntity blogEntity = BlogEntity.builder()
                .title(blogDTO.getTitle())
                .date(blogDTO.getDate())
                .location(blogDTO.getLocation())
                .paragraph(blogDTO.getParagraph())
                .author(blogDTO.getAuthor())
                .coverImageUrl(blogDTO.getCoverImageUrl())
                .slug(blogDTO.getSlug())
                .images(blogImages)
                .tags(blogDTO.getTags() != null ? blogDTO.getTags() : new ArrayList<>())
                .build();

        // Part 5: Save the blog to the database
        BlogEntity savedBlog = blogRepository.save(blogEntity);
        log.info("Blog saved successfully with ID: {}", savedBlog.getId());

        // Part 6: Convert and return BlogModel
//      return convertToBlogModel(savedBlog);
        return blogProcessingUtil.convertToBlogModel(savedBlog);
    }
}

/// / Helper method to upload and process images
/// / TODO: Update this to be a reusable method in imageServiceImpl
//private List<ImageEntity> uploadAndProcessImages(List<MultipartFile> images, String type) {
//    List<ImageEntity> imageEntities = new ArrayList<>();
//
//    for (MultipartFile image : images) {
//        try (InputStream inputStream = image.getInputStream()) {
//            // Extract metadata (reuse your existing code)
//            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
//            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata
//        } catch (Exception e) {
//            log.error("Error processing image for block: {}", e.getMessage());
//        }
//    }
//}
//}
