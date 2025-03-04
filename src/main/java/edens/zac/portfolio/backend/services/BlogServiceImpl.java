package edens.zac.portfolio.backend.services;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Creates a new blog with any information, associated images, or new images.
     * Any new images will also use `ImageProcessingUtil` methods to create images.
     * <p>
     * if any part of blog creation fails, 'database transaction' will verify all database changes are rolled back.
     *
     * @param blogDTO blog object
     * @param images  list of images to upload and associate with our new blog
     * @return BlogModel of our new blog
     */
    @Override
    @Transactional
    public BlogModel createBlog(BlogCreateDTO blogDTO, List<MultipartFile> images) {

        // Part 1: Initialize image collection
        Set<ImageEntity> blogImages = new HashSet<>();

        // Part 2: Process existing images if IDs were provided
        if (blogDTO.getExistingImageIds() != null && !blogDTO.getExistingImageIds().isEmpty()) {
            log.info("Processing {} existing images", blogDTO.getExistingImageIds().size());
            blogDTO.getExistingImageIds().stream()
                    .map(imageRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(blogImages::add);
        }

        // Part 3: Process and upload new images, if any
        if (images != null && !images.isEmpty()) {
            log.info("Processing {} new uploaded images", images.size());
            for (MultipartFile image : images) {
                try {
                    // Process Image
                    ImageEntity imageEntity = imageProcessingUtil.processAndSaveImage(image, "blog", blogDTO.getTitle());

                    if (imageEntity == null) {
                        log.warn("Failed to process image {}", image.getOriginalFilename());
                        continue;
                    }

                    blogImages.add(imageEntity);

                    // If this is the first image and no cover image was specified, use as cover
                    if (blogDTO.getCoverImageUrl() == null &&
                            imageEntity.getImageUrlWeb() != null) {
                        blogDTO.setCoverImageUrl(imageEntity.getImageUrlWeb());
                    }
                } catch (Exception e) {
                    log.error("Error processing image for blog: {}: {}", image.getOriginalFilename(), e.getMessage(), e);
                    // Continue with other images instead of failing the whole return
                }
            }
        }

        // Part 4: Create Blog Entity
        BlogEntity blogEntity = BlogEntity.builder()
                .title(blogDTO.getTitle())
                .date(LocalDate.now())
                .location(blogDTO.getLocation())
                .paragraph(blogDTO.getParagraph())
                .author(blogDTO.getAuthor())
                .coverImageUrl(blogDTO.getCoverImageUrl())
                .slug(blogDTO.getSlug() != null && !blogDTO.getSlug().isEmpty() ?
                        blogDTO.getSlug() : blogProcessingUtil.generateSlug(blogDTO.getTitle()))
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

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BlogModel getBlogById(Long id) {
        log.info("Fetching blog with ID: {}", id);

        // Get the blog entity with all its images
        Optional<BlogEntity> blogOpt = blogRepository.findById(id);

        if (blogOpt.isEmpty()) {
            log.warn("Blog with ID {} not found", id);
            return null;
        }

        Optional<BlogEntity> blogEntityOpt = blogRepository.findByIdWithImages(id);

        if (blogEntityOpt.isEmpty()) {
            log.warn("Blog with ID {} not found with custom query", id);
            return blogProcessingUtil.convertToBlogModel(blogOpt.get());
        }

        log.info("Blog found with ID={}, title={}, images={}",
                blogEntityOpt.get().getId(),
                blogEntityOpt.get().getTitle(),
                blogEntityOpt.get().getImages().size()
        );

        // Convert entity to model using utils
        return blogProcessingUtil.convertToBlogModel(blogEntityOpt.get());

    }
}

