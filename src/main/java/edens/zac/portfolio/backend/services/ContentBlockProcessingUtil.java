package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCameraRepository;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
import edens.zac.portfolio.backend.repository.ContentLensRepository;
import edens.zac.portfolio.backend.types.ContentBlockType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Utility class for processing content blocks.
 * Handles conversion between entities and models, content validation,
 * and specialized processing for different content block types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentBlockProcessingUtil {

    // Dependencies for S3 upload and repositories
    private final AmazonS3 amazonS3;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentCameraRepository contentCameraRepository;
    private final ContentCollectionRepository contentCollectionRepository;
    private final ContentLensRepository contentLensRepository;
    private final edens.zac.portfolio.backend.repository.ContentFilmTypeRepository contentFilmTypeRepository;

    @Value("${aws.portfolio.s3.bucket}")
    private String bucketName;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

    // Default values for image metadata
    public static final class DEFAULT {
        public static final String AUTHOR = "Zechariah Edens";
    }

    // Supported programming languages for code blocks
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "java", "javascript", "typescript", "python", "html", "css",
            "sql", "bash", "shell", "json", "xml", "yaml", "markdown",
            "plaintext", "c", "cpp", "csharp", "go", "rust", "kotlin", "swift"
    );

    /**
     * Convert a ContentBlockEntity to its corresponding ContentBlockModel based on type.
     *
     * @param entity The content block entity to convert
     * @return The corresponding content block model
     */
    public ContentBlockModel convertToModel(ContentBlockEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity.getBlockType() == null) {
            log.error("Unknown content block type: null");
            throw new IllegalArgumentException("Unknown content block type: null");
        }

        return switch (entity.getBlockType()) {
            case IMAGE -> convertImageToModel((ImageContentBlockEntity) entity);
            case TEXT -> convertTextToModel((TextContentBlockEntity) entity);
            case CODE -> convertCodeToModel((CodeContentBlockEntity) entity);
            case GIF -> convertGifToModel((GifContentBlockEntity) entity);
        };
    }

    /**
     * Copy base properties from a ContentBlockEntity to a ContentBlockModel.
     *
     * @param entity The source entity
     * @param model  The target model
     */
    private void copyBaseProperties(ContentBlockEntity entity, ContentBlockModel model) {
        model.setId(entity.getId());
        model.setCollectionId(entity.getCollectionId());
        model.setOrderIndex(entity.getOrderIndex());
        model.setBlockType(entity.getBlockType());
        model.setCaption(entity.getCaption());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
    }

    public static ContentCameraModel cameraEntityToCameraModel(ContentCameraEntity entity) {
        return ContentCameraModel.builder()
                .id(entity.getId())
                .name(entity.getCameraName())
                .build();
    }

    public static ContentLensModel lensEntityToLensModel(ContentLensEntity entity) {
        return ContentLensModel.builder()
                .id(entity.getId())
                .name(entity.getLensName())
                .build();
    }

    /**
     * Convert an ImageContentBlockEntity to an ImageContentBlockModel.
     *
     * @param entity The image content block entity to convert
     * @return The corresponding image content block model
     */
    private ImageContentBlockModel convertImageToModel(ImageContentBlockEntity entity) {
        if (entity == null) {
            return null;
        }

        ImageContentBlockModel model = new ImageContentBlockModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy image-specific properties
        model.setTitle(entity.getTitle());
        model.setImageWidth(entity.getImageWidth());
        model.setImageHeight(entity.getImageHeight());
        model.setIso(entity.getIso());
        model.setAuthor(entity.getAuthor());
        model.setRating(entity.getRating());
        model.setFStop(entity.getFStop());
        model.setLens(entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null);
        model.setBlackAndWhite(entity.getBlackAndWhite());
        model.setIsFilm(entity.getIsFilm());
        // Convert film type entity to display name
        model.setFilmType(entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null);
        model.setFilmFormat(entity.getFilmFormat());
        model.setShutterSpeed(entity.getShutterSpeed());
        model.setImageUrlFullSize(entity.getImageUrlFullSize());
        model.setCamera(entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null);
        model.setFocalLength(entity.getFocalLength());
        model.setLocation(entity.getLocation());
        model.setImageUrlWeb(entity.getImageUrlWeb());
        model.setCreateDate(entity.getCreateDate());

        // Map tags - convert entities to simplified tag objects (id and name only)
        if (entity.getTags() != null && !entity.getTags().isEmpty()) {
            List<edens.zac.portfolio.backend.model.ContentBlockTagModel> tagModels = entity.getTags().stream()
                    .map(tag -> edens.zac.portfolio.backend.model.ContentBlockTagModel.builder()
                            .id(tag.getId())
                            .name(tag.getTagName())
                            .build())
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
            model.setTags(tagModels);
        }

        // Map people - convert entities to simplified person objects (id and name only)
        if (entity.getPeople() != null && !entity.getPeople().isEmpty()) {
            List<edens.zac.portfolio.backend.model.ContentBlockPersonModel> personModels = entity.getPeople().stream()
                    .map(person -> edens.zac.portfolio.backend.model.ContentBlockPersonModel.builder()
                            .id(person.getId())
                            .name(person.getPersonName())
                            .build())
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
            model.setPeople(personModels);
        }

        // Populate collections array - fetch all collections this image belongs to
        List<ImageContentBlockEntity> instances = new ArrayList<>();
        if (entity.getFileIdentifier() != null) {
            instances = contentBlockRepository.findAllByFileIdentifier(entity.getFileIdentifier());
        } else {
            // Fallback if no fileIdentifier - just use the current entity
            instances.add(entity);
        }

        List<ImageCollection> collections = new ArrayList<>();
        for (ImageContentBlockEntity instance : instances) {
            if (instance.getCollectionId() != null) {
                contentCollectionRepository.findById(instance.getCollectionId())
                        .ifPresent(collection -> {
                            ImageCollection ic = ImageCollection.builder()
                                    .collectionId(collection.getId())
                                    .name(collection.getTitle())
                                    .visible(instance.getVisible() != null ? instance.getVisible() : true)
                                    .orderIndex(instance.getOrderIndex())
                                    .build();
                            collections.add(ic);
                        });
            }
        }
        model.setCollections(collections);

        return model;
    }

    /**
     * Convert a TextContentBlockEntity to a TextContentBlockModel.
     *
     * @param entity The text content block entity to convert
     * @return The corresponding text content block model
     */
    private TextContentBlockModel convertTextToModel(TextContentBlockEntity entity) {
        if (entity == null) {
            return null;
        }

        TextContentBlockModel model = new TextContentBlockModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy text-specific properties
        model.setContent(entity.getContent());
        model.setFormatType(entity.getFormatType());

        return model;
    }

    /**
     * Convert a CodeContentBlockEntity to a CodeContentBlockModel.
     *
     * @param entity The code content block entity to convert
     * @return The corresponding code content block model
     */
    private CodeContentBlockModel convertCodeToModel(CodeContentBlockEntity entity) {
        if (entity == null) {
            return null;
        }

        CodeContentBlockModel model = new CodeContentBlockModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy code-specific properties
        model.setCode(entity.getCode());
        model.setLanguage(entity.getLanguage());
        model.setTitle(entity.getTitle());

        // Set default values for additional properties
        model.setShowLineNumbers(true);

        return model;
    }

    /**
     * Convert a GifContentBlockEntity to a GifContentBlockModel.
     *
     * @param entity The gif content block entity to convert
     * @return The corresponding gif content block model
     */
    private GifContentBlockModel convertGifToModel(GifContentBlockEntity entity) {
        if (entity == null) {
            return null;
        }

        GifContentBlockModel model = new GifContentBlockModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy gif-specific properties
        model.setTitle(entity.getTitle());
        model.setGifUrl(entity.getGifUrl());
        model.setThumbnailUrl(entity.getThumbnailUrl());
        model.setWidth(entity.getWidth());
        model.setHeight(entity.getHeight());
        model.setAuthor(entity.getAuthor());
        model.setCreateDate(entity.getCreateDate());

        // Map tags - convert entities to tag names
        if (entity.getTags() != null && !entity.getTags().isEmpty()) {
            List<String> tagNames = entity.getTags().stream()
                    .map(ContentTagEntity::getTagName)
                    .sorted()
                    .toList();
            model.setTags(tagNames);
        }

        return model;
    }

    /**
     * Process and save a content block based on its type.
     *
     * @param file         The file to process (for media content blocks)
     * @param type         The type of content block
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex   The order index of this block within the collection
     * @return The saved content block entity
     */
    public ContentBlockEntity processContentBlock(
            MultipartFile file,
            ContentBlockType type,
            Long collectionId,
            Integer orderIndex,
            String content,
            String language,
            String title,
            String caption
    ) {
        log.info("Processing content block of type {} for collection {}", type, collectionId);

        if (type == null) {
            log.error("Unknown content block type: null");
            throw new IllegalArgumentException("Unknown content block type: null");
        }

        return switch (type) {
            case IMAGE -> processImageContentBlock(file, collectionId, orderIndex, title, caption);
            case TEXT -> processTextContentBlock(content, collectionId, orderIndex, caption);
            case CODE -> processCodeContentBlock(content, language, collectionId, orderIndex, title, caption);
            case GIF -> processGifContentBlock(file, collectionId, orderIndex, title, caption);
        };
    }

    /**
     * Process and save a text content block.
     *
     * @param text         The text content
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex   The order index of this block within the collection
     * @param caption      The caption for the text block
     * @return The saved text content block entity
     */
    public TextContentBlockEntity processTextContentBlock(
            String text,
            Long collectionId,
            Integer orderIndex,
            String caption
    ) {
        log.info("Processing text content block for collection {}", collectionId);

        try {
            // Validate and sanitize the text content
            String validatedContent = validateAndSanitizeTextContent(text);

            // Create and return the text content block entity
            TextContentBlockEntity entity = new TextContentBlockEntity();
            entity.setCollectionId(collectionId);
            entity.setOrderIndex(orderIndex);
            entity.setBlockType(ContentBlockType.TEXT);
            entity.setCaption(caption);
            entity.setContent(validatedContent);
            entity.setFormatType("markdown"); // Default format type

            // Save the entity using the repository
            return contentBlockRepository.save(entity);

        } catch (Exception e) {
            log.error("Error processing text content block: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process text content block", e);
        }
    }

    /**
     * Process and save a code content block.
     *
     * @param code         The code content
     * @param language     The programming language
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex   The order index of this block within the collection
     * @param title        The title of the code block
     * @param caption      The caption for the code block
     * @return The saved code content block entity
     */
    public CodeContentBlockEntity processCodeContentBlock(
            String code,
            String language,
            Long collectionId,
            Integer orderIndex,
            String title,
            String caption
    ) {
        log.info("Processing code content block for collection {}", collectionId);

        try {
            // Validate the code content and language
            String validatedCode = validateCodeContent(code, language);
            String validatedLanguage = validateCodeLanguage(language);

            // Create and return the code content block entity
            CodeContentBlockEntity entity = new CodeContentBlockEntity();
            entity.setCollectionId(collectionId);
            entity.setOrderIndex(orderIndex);
            entity.setBlockType(ContentBlockType.CODE);
            entity.setCaption(caption);
            entity.setCode(validatedCode);
            entity.setLanguage(validatedLanguage);
            entity.setTitle(title);

            // Save the entity using the repository
            return contentBlockRepository.save(entity);

        } catch (Exception e) {
            log.error("Error processing code content block: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process code content block", e);
        }
    }

    /**
     * Process and save a gif content block.
     *
     * @param file         The gif file to process
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex   The order index of this block within the collection
     * @param title        The title of the gif
     * @param caption      The caption for the gif
     * @return The saved gif content block entity
     */
    public GifContentBlockEntity processGifContentBlock(
            MultipartFile file,
            Long collectionId,
            Integer orderIndex,
            String title,
            String caption
    ) {
        log.info("Processing gif content block for collection {}", collectionId);

        try {
            // Validate input
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("GIF file cannot be empty");
            }

            // Upload GIF to S3 and get URLs
            Map<String, String> urls = uploadGifToS3(file);
            String gifUrl = urls.get("gifUrl");
            String thumbnailUrl = urls.get("thumbnailUrl");

            // Get dimensions from the first frame
            BufferedImage firstFrame = ImageIO.read(file.getInputStream());
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // Create the gif content block entity
            GifContentBlockEntity entity = new GifContentBlockEntity();
            entity.setCollectionId(collectionId);
            entity.setOrderIndex(orderIndex);
            entity.setBlockType(ContentBlockType.GIF);
            entity.setCaption(caption);
            entity.setTitle(title != null ? title : file.getOriginalFilename());
            entity.setGifUrl(gifUrl);
            entity.setThumbnailUrl(thumbnailUrl);
            entity.setWidth(width);
            entity.setHeight(height);
            entity.setAuthor("System"); // Default author
            entity.setCreateDate(LocalDate.now().toString());

            // Save the entity using the repository
            return contentBlockRepository.save(entity);

        } catch (Exception e) {
            log.error("Error processing gif content block: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process GIF content block", e);
        }
    }

    /**
     * Reorder content blocks within a collection.
     *
     * @param collectionId The ID of the collection
     * @param blockIds     The ordered list of block IDs
     * @return The updated list of content block entities
     */
    public List<ContentBlockEntity> reorderContentBlocks(Long collectionId, List<Long> blockIds) {
        log.info("Reordering content blocks for collection {}", collectionId);

        if (blockIds == null || blockIds.isEmpty()) {
            throw new IllegalArgumentException("Block IDs list cannot be empty");
        }

        try {
            // Get all blocks for this collection
            List<ContentBlockEntity> blocks = contentBlockRepository.findByCollectionIdOrderByOrderIndex(collectionId);

            // Create a map of block ID to entity for quick lookup
            Map<Long, ContentBlockEntity> blockMap = new HashMap<>();
            for (ContentBlockEntity block : blocks) {
                blockMap.put(block.getId(), block);
            }

            // Validate that all block IDs belong to this collection
            for (Long blockId : blockIds) {
                ContentBlockEntity block = blockMap.get(blockId);
                if (block == null) {
                    throw new IllegalArgumentException("Block ID " + blockId + " does not belong to collection " + collectionId);
                }
            }

            // Update order index for each block based on its position in the list
            List<ContentBlockEntity> updatedBlocks = new ArrayList<>();
            for (int i = 0; i < blockIds.size(); i++) {
                Long blockId = blockIds.get(i);
                ContentBlockEntity block = blockMap.get(blockId);
                block.setOrderIndex(i);
                updatedBlocks.add(block);
            }

            // Save all updated blocks
            return contentBlockRepository.saveAll(updatedBlocks);

        } catch (Exception e) {
            log.error("Error reordering content blocks: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reorder content blocks", e);
        }
    }

    /**
     * Validate and sanitize text content.
     *
     * @param content The text content to validate and sanitize
     * @return The validated and sanitized text content
     */
    private String validateAndSanitizeTextContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Text content cannot be empty");
        }

        // Check for maximum length
        if (content.length() > 10000) {
            log.warn("Text content exceeds maximum length, truncating");
            content = content.substring(0, 10000);
        }

        // Sanitize content to prevent XSS attacks
        // Since we don't have Apache Commons Text with StringEscapeUtils,
        // we'll use a simple approach to escape HTML special characters
        content = sanitizeHtml(content);

        return content;
    }

    /**
     * Sanitize HTML content to prevent XSS attacks.
     *
     * @param html The HTML content to sanitize
     * @return The sanitized HTML content
     */
    private String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }

        // Replace HTML special characters with their escaped versions
        return html.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * Validate and process code content.
     *
     * @param content  The code content to validate
     * @param language The programming language
     * @return The validated code content
     */
    private String validateCodeContent(String content, String language) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Code content cannot be empty");
        }

        // Check for maximum length
        if (content.length() > 50000) {
            log.warn("Code content exceeds maximum length, truncating");
            content = content.substring(0, 50000);
        }

        // Sanitize code content to prevent XSS attacks
        // For code blocks, we need to escape HTML but preserve formatting
        content = sanitizeHtml(content);

        // Format code based on language
        // This is a simplified implementation - in a real-world scenario,
        // you might use a code formatting library specific to each language
        String validatedLanguage = validateCodeLanguage(language);

        // For certain languages, we might want to add specific formatting
        // This is just a placeholder for language-specific formatting
        switch (validatedLanguage) {
            case "html":
                // For HTML, we might want to ensure proper indentation
                content = formatHtmlCode(content);
                break;
            case "java":
            case "javascript":
            case "typescript":
                // For these languages, we might want to ensure proper braces
                content = ensureProperBraces(content);
                break;
            default:
                // No special formatting for other languages
                break;
        }

        return content;
    }

    /**
     * Validate and normalize programming language.
     *
     * @param language The programming language to validate
     * @return The validated and normalized language
     */
    private String validateCodeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            // Default to plain text if no language is specified
            return "plaintext";
        }

        // Normalize language name (lowercase, trim)
        String normalizedLanguage = language.toLowerCase().trim();

        // Validate against the list of supported languages
        if (!SUPPORTED_LANGUAGES.contains(normalizedLanguage)) {
            log.warn("Unsupported language: {}. Defaulting to plaintext.", normalizedLanguage);
            return "plaintext";
        }

        return normalizedLanguage;
    }

    /**
     * Format HTML code for better readability.
     * This is a simplified implementation.
     *
     * @param htmlCode The HTML code to format
     * @return The formatted HTML code
     */
    private String formatHtmlCode(String htmlCode) {
        // This is a placeholder for HTML formatting logic
        // In a real implementation, you might use a library like jsoup
        return htmlCode;
    }

    /**
     * Ensure proper braces in code.
     * This is a simplified implementation.
     *
     * @param code The code to format
     * @return The formatted code
     */
    private String ensureProperBraces(String code) {
        // This is a placeholder for brace formatting logic
        // In a real implementation, you might use a language-specific formatter
        return code;
    }

    /**
     * Upload a GIF to S3.
     *
     * @param file The GIF file to upload
     * @return A map containing the S3 URLs for the GIF and its thumbnail
     * @throws IOException If there's an error processing the file
     */
    private Map<String, String> uploadGifToS3(MultipartFile file) throws IOException {
        log.info("Uploading GIF to S3: {}", file.getOriginalFilename());

        // Validate file is a GIF
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("gif")) {
            throw new IllegalArgumentException("File is not a GIF");
        }

        // Read the file
        byte[] fileBytes = file.getBytes();

        // Generate S3 keys
        String date = LocalDate.now().toString();
        String gifS3Key = generateS3Key(date, file.getOriginalFilename(), "gif");
        String thumbnailS3Key = generateS3Key(date, Objects.requireNonNull(file.getOriginalFilename()).replace(".gif", "-thumbnail.jpg"), "thumbnail");

        // Upload GIF to S3
        ByteArrayInputStream gifStream = new ByteArrayInputStream(fileBytes);
        ObjectMetadata gifMetadata = new ObjectMetadata();
        gifMetadata.setContentType(contentType);
        gifMetadata.setContentLength(fileBytes.length);

        PutObjectRequest putGifRequest = new PutObjectRequest(
                bucketName,
                gifS3Key,
                gifStream,
                gifMetadata
        );

        amazonS3.putObject(putGifRequest);

        // Generate thumbnail from first frame of GIF
        BufferedImage firstFrame = ImageIO.read(new ByteArrayInputStream(fileBytes));
        ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
        ImageIO.write(firstFrame, "jpg", thumbnailOutput);
        byte[] thumbnailBytes = thumbnailOutput.toByteArray();

        // Upload thumbnail to S3
        ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(thumbnailBytes);
        ObjectMetadata thumbnailMetadata = new ObjectMetadata();
        thumbnailMetadata.setContentType("image/jpeg");
        thumbnailMetadata.setContentLength(thumbnailBytes.length);

        PutObjectRequest putThumbnailRequest = new PutObjectRequest(
                bucketName,
                thumbnailS3Key,
                thumbnailStream,
                thumbnailMetadata
        );

        amazonS3.putObject(putThumbnailRequest);

        // Return CloudFront URLs
        String gifUrl = "https://" + cloudfrontDomain + "/" + gifS3Key;
        String thumbnailUrl = "https://" + cloudfrontDomain + "/" + thumbnailS3Key;

        log.info("GIF uploaded successfully. URL: {}", gifUrl);
        log.info("Thumbnail uploaded successfully. URL: {}", thumbnailUrl);

        Map<String, String> urls = new HashMap<>();
        urls.put("gifUrl", gifUrl);
        urls.put("thumbnailUrl", thumbnailUrl);

        return urls;
    }

    /**
     * Generate an S3 key for a file.
     *
     * @param date     The date to use in the key
     * @param filename The filename
     * @param type     The type of file (gif, thumbnail, etc.)
     * @return The generated S3 key
     */
    private String generateS3Key(String date, String filename, String type) {
        return String.format("%s/%s/%s", date, type, filename);
    }

    /**
     * Check if a file is a JPG/JPEG file.
     *
     * @param file The file to check
     * @return true if the file is a JPG/JPEG, false otherwise
     */
    private boolean isJpgFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return (contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/jpg"))) ||
                (filename != null && (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")));
    }

    // ============================================================================
    // NEW STREAMLINED IMAGE PROCESSING METHODS
    // ============================================================================

    /**
     * STREAMLINED: Process and save an image content block.
     * <p>
     * Flow:
     * 1. Extract metadata from original file
     * 2. Upload original full-size image to S3
     * 3. Convert JPG → WebP (with compression)
     * 4. Resize if needed
     * 5. Upload web-optimized image to S3
     * 6. Save metadata with both URLs to database
     * 7. Return ImageContentBlockEntity
     *
     * @param file         The image file to process
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex   The order index of this block within the collection
     * @param title        The title of the image
     * @param caption      The caption for the image
     * @return The saved image content block entity
     */
    public ImageContentBlockEntity processImageContentBlock(
            MultipartFile file,
            Long collectionId,
            Integer orderIndex,
            String title,
            String caption
    ) {
        log.info("STREAMLINED: Processing image content block for collection {}", collectionId);

        try {
            // STEP 1: Extract metadata from original file (before any conversion)
            log.info("Step 1: Extracting metadata from original file");
            Map<String, String> metadata = extractImageMetadata(file);

            // STEP 2: Upload original full-size image to S3
            log.info("Step 2: Uploading original full-size image to S3");
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String imageUrlFullSize = uploadImageToS3(file.getBytes(), originalFilename, contentType, "full");

            // STEP 3: Resize FIRST if needed (max 2500px on longest side)
            // We resize BEFORE converting to WebP to avoid having to decode WebP back to BufferedImage
            log.info("Step 3: Resizing original image if needed");
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new IOException("Failed to read image: " + originalFilename);
            }

            BufferedImage resizedImage = resizeImage(originalImage, metadata, 2500);

            // STEP 4: Convert to WebP (includes compression)
            log.info("Step 4: Converting to WebP and compressing");
            byte[] processedImageBytes;
            String finalFilename;

            if (isJpgFile(file) || isWebPFile(file)) {
                processedImageBytes = convertJpgToWebP(resizedImage);
                assert originalFilename != null;
                finalFilename = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");
            } else {
                throw new IOException("Unsupported file format. Only JPG and WebP are supported.");
            }

            // STEP 5: Upload web-optimized image to S3
            log.info("Step 5: Uploading web-optimized image to S3");
            String imageUrlWeb = uploadImageToS3(processedImageBytes, finalFilename, "image/webp", "webP");

            // STEP 6: Create and save ImageContentBlockEntity with metadata
            log.info("Step 6: Saving to database");

            // Generate file identifier for duplicate detection (format: "YYYY-MM-DD/filename.jpg")
            String date = LocalDate.now().toString();
            String fileIdentifier = date + "/" + originalFilename;

            ImageContentBlockEntity entity = ImageContentBlockEntity.builder()
                    .collectionId(collectionId)
                    .orderIndex(orderIndex)
                    .blockType(ContentBlockType.IMAGE)
                    .caption(caption)
                    .title(title != null ? title : metadata.getOrDefault("title", finalFilename))
                    .imageWidth(parseIntegerOrDefault(metadata.get("imageWidth"), 0))
                    .imageHeight(parseIntegerOrDefault(metadata.get("imageHeight"), 0))
                    .iso(parseIntegerOrDefault(metadata.get("iso"), null))
                    .author(metadata.getOrDefault("author", DEFAULT.AUTHOR))
                    .rating(parseIntegerOrDefault(metadata.get("rating"), null))
                    .fStop(metadata.get("fStop"))
                    .blackAndWhite(parseBooleanOrDefault(metadata.get("blackAndWhite"), false))
                    .isFilm(metadata.get("fStop") == null)
                    .shutterSpeed(metadata.get("shutterSpeed"))
                    .imageUrlFullSize(imageUrlFullSize)
                    .focalLength(metadata.get("focalLength"))
                    .location(metadata.get("location"))
                    .imageUrlWeb(imageUrlWeb)
                    .createDate(metadata.getOrDefault("createDate", LocalDate.now().toString()))
                    .fileIdentifier(fileIdentifier)
                    .build();

            // Handle camera - find existing or create new from metadata
            String cameraName = metadata.get("camera");
            if (cameraName != null && !cameraName.trim().isEmpty()) {
                ContentCameraEntity camera = contentCameraRepository.findByCameraNameIgnoreCase(cameraName.trim())
                        .orElseGet(() -> {
                            log.info("Creating new camera from metadata: {}", cameraName);
                            ContentCameraEntity newCamera = new ContentCameraEntity(cameraName.trim());
                            return contentCameraRepository.save(newCamera);
                        });
                entity.setCamera(camera);
            }

            // Handle lens - find existing or create new from metadata
            String lensName = metadata.get("lens");
            if (lensName != null && !lensName.trim().isEmpty()) {
                ContentLensEntity lens = contentLensRepository.findByLensNameIgnoreCase(lensName.trim())
                        .orElseGet(() -> {
                            log.info("Creating new lens from metadata: {}", lensName);
                            ContentLensEntity newLens = new ContentLensEntity(lensName.trim());
                            return contentLensRepository.save(newLens);
                        });
                entity.setLens(lens);
            }

            // STEP 7: Save and return
            ImageContentBlockEntity savedEntity = contentBlockRepository.save(entity);
            log.info("Successfully processed image content block with ID: {}", savedEntity.getId());
            return savedEntity;

        } catch (Exception e) {
            log.error("Error processing image content block: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract metadata from an image file using Drew Noakes metadata-extractor.
     *
     * @param file The image file to extract metadata from
     * @return Map of metadata key-value pairs
     * @throws IOException If there's an error reading the file
     */
    private Map<String, String> extractImageMetadata(MultipartFile file) throws IOException {
        Map<String, String> metadata = new HashMap<>();

        try (InputStream inputStream = file.getInputStream()) {
            Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

            // Extract all metadata tags
            for (Directory directory : imageMetadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName();
                    String description = tag.getDescription();

                    if (description != null && !description.isEmpty()) {
                        // Map common EXIF tags to our metadata keys
                        switch (tagName) {
                            case "Image Width" -> metadata.put("imageWidth", description.replaceAll("[^0-9]", ""));
                            case "Image Height" -> metadata.put("imageHeight", description.replaceAll("[^0-9]", ""));
                            case "ISO Speed Ratings" -> metadata.put("iso", description);
                            case "Artist" -> metadata.put("author", description);
                            case "Rating" -> metadata.put("rating", description);
                            case "F-Number" -> metadata.put("fStop", description);
                            case "Lens Model", "Lens" -> metadata.put("lens", description);
                            case "Exposure Time" -> metadata.put("shutterSpeed", description);
                            case "Model" -> metadata.put("camera", description);
                            case "Focal Length" -> metadata.put("focalLength", description);
                            case "GPS Latitude", "GPS Longitude" -> {
                                String existingLocation = metadata.getOrDefault("location", "");
                                metadata.put("location", existingLocation + " " + description);
                            }
                            case "Date/Time Original", "Date/Time" -> metadata.put("createDate", description);
                        }
                    }
                }
            }

            // Check for XMP data for additional metadata
            for (Directory directory : imageMetadata.getDirectories()) {
                if (directory instanceof XmpDirectory xmpDirectory) {
                    String xmpXml = xmpDirectory.getXMPMeta().dumpObject();

                    // Check for film simulation indicators
                    if (xmpXml.contains("Film") || xmpXml.contains("film")) {
                        metadata.put("isFilm", "true");
                    }

                    // Check for black and white
                    if (xmpXml.contains("Monochrome") || xmpXml.contains("BlackAndWhite")) {
                        metadata.put("blackAndWhite", "true");
                    }
                }
            }

            // If we couldn't get dimensions from metadata, read from BufferedImage
            if (!metadata.containsKey("imageWidth") || !metadata.containsKey("imageHeight")) {
                try (InputStream is2 = file.getInputStream()) {
                    BufferedImage img = ImageIO.read(is2);
                    if (img != null) {
                        metadata.put("imageWidth", String.valueOf(img.getWidth()));
                        metadata.put("imageHeight", String.valueOf(img.getHeight()));
                    }
                }
            }

            log.info("Extracted metadata: {} tags", metadata.size());

        } catch (Exception e) {
            log.warn("Failed to extract full metadata: {}", e.getMessage());

            // Fallback: At minimum, get dimensions from BufferedImage
            try (InputStream is = file.getInputStream()) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    metadata.put("imageWidth", String.valueOf(img.getWidth()));
                    metadata.put("imageHeight", String.valueOf(img.getHeight()));
                }
            }
        }

        return metadata;
    }

    /**
     * Check if a file is a WebP file.
     *
     * @param file The file to check
     * @return true if the file is WebP, false otherwise
     */
    private boolean isWebPFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return (contentType != null && contentType.equals("image/webp")) ||
                (filename != null && filename.toLowerCase().endsWith(".webp"));
    }

    /**
     * Upload an image to S3 and return the CloudFront URL.
     *
     * @param imageBytes  The image bytes to upload
     * @param filename    The filename
     * @param contentType The content type of the image (e.g., "image/jpeg", "image/webp")
     * @param folderType  The folder type for S3 path ("full" for originals, "webP" for optimized)
     * @return The CloudFront URL of the uploaded image
     */
    private String uploadImageToS3(byte[] imageBytes, String filename, String contentType, String folderType) {
        String date = LocalDate.now().toString();
        String s3Key = String.format("images/%s/%s/%s", folderType, date, filename);

        log.info("Uploading {} image to S3: {}", folderType, s3Key);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(imageBytes.length);

        PutObjectRequest putRequest = new PutObjectRequest(
                bucketName,
                s3Key,
                inputStream,
                metadata
        );

        amazonS3.putObject(putRequest);

        String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + s3Key;
        log.info("Successfully uploaded {} image: {}", folderType, cloudfrontUrl);

        return cloudfrontUrl;
    }

    /**
     * Resize a BufferedImage to fit within the maximum dimension.
     * This method resizes the ORIGINAL image format (JPG/PNG) before WebP conversion,
     * avoiding the need to decode WebP back to BufferedImage which causes native library crashes.
     * If the image is already within the size limits, it returns the original unchanged.
     *
     * @param originalImage The original BufferedImage to resize
     * @param metadata      The image metadata containing dimensions (will be updated)
     * @param maxDimension  The maximum allowed dimension (width or height)
     * @return Resized BufferedImage, or original if no resize needed
     */
    private BufferedImage resizeImage(BufferedImage originalImage, Map<String, String> metadata, int maxDimension) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Calculate new dimensions
        int newWidth, newHeight;
        boolean needsResize = false;

        if (originalWidth > originalHeight) {
            if (originalWidth > maxDimension) {
                newWidth = maxDimension;
                newHeight = (int) (originalHeight * (((double) maxDimension / originalWidth)));
                needsResize = true;
            } else {
                newWidth = originalWidth;
                newHeight = originalHeight;
            }
        } else {
            if (originalHeight > maxDimension) {
                newHeight = maxDimension;
                newWidth = (int) (originalWidth * (((double) maxDimension / originalHeight)));
                needsResize = true;
            } else {
                newHeight = originalHeight;
                newWidth = originalWidth;
            }
        }

        if (!needsResize) {
            log.info("Image is within size limits ({}x{}), no resize needed", originalWidth, originalHeight);
            return originalImage;
        }

        log.info("Resizing image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);

        // Create resized image with proper color model
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Update metadata with new dimensions
        metadata.put("imageWidth", String.valueOf(newWidth));
        metadata.put("imageHeight", String.valueOf(newHeight));

        return resizedImage;
    }

    /**
     * Convert a BufferedImage to WebP format with compression.
     * This method accepts an already-resized BufferedImage, avoiding the need to decode WebP.
     *
     * @param bufferedImage The BufferedImage to convert
     * @return byte array containing the WebP image data
     * @throws IOException If there's an error during conversion
     */
    private byte[] convertJpgToWebP(BufferedImage bufferedImage) throws IOException {
        log.info("Converting BufferedImage to WebP: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());

        // Create a ByteArrayOutputStream to capture the WebP bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Get a WebP ImageWriter from ImageIO
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("No WebP writer found. Make sure webp-imageio is on the classpath.");
        }

        ImageWriter writer = writers.next();
        log.info("Using WebP writer: {}", writer.getClass().getName());

        // Configure compression settings for the WebP output
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            String[] compressionTypes = writeParam.getCompressionTypes();
            if (compressionTypes != null && compressionTypes.length > 0) {
                writeParam.setCompressionType(compressionTypes[0]);
            }
            writeParam.setCompressionQuality(0.85f); // 85% quality
            log.info("Set WebP compression quality to 85%");
        } else {
            log.warn("WebP writer does not support compression settings");
        }

        // Write the BufferedImage to the ByteArrayOutputStream in WebP format
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(bufferedImage, null, null), writeParam);
            writer.dispose();
        }

        byte[] webpBytes = outputStream.toByteArray();
        log.info("Successfully converted BufferedImage to WebP. WebP size: {} bytes", webpBytes.length);

        return webpBytes;
    }

    /**
     * Parse a string to an Integer, returning a default value if parsing fails.
     *
     * @param value        The string value to parse
     * @param defaultValue The default value to return if parsing fails
     * @return The parsed integer or default value
     */
    private Integer parseIntegerOrDefault(String value, Integer defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            // Remove any non-numeric characters except minus sign
            String cleaned = value.replaceAll("[^0-9-]", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse a string to a Boolean, returning a default value if parsing fails.
     *
     * @param value        The string value to parse
     * @param defaultValue The default value to return if parsing fails
     * @return The parsed boolean or default value
     */
    private Boolean parseBooleanOrDefault(String value, Boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value) || value.equalsIgnoreCase("true") || value.equals("1");
    }

    // =============================================================================
    // IMAGE UPDATE HELPERS (following the pattern from ContentCollectionProcessingUtil)
    // =============================================================================

    /**
     * Apply partial updates from ImageUpdateRequest to an ImageContentBlockEntity.
     * Only fields provided in the update request will be updated.
     * This follows the same pattern as ContentCollectionProcessingUtil.applyBasicUpdates.
     *
     * @param entity The image entity to update
     * @param updateRequest The update request containing the fields to update
     */
    public void applyImageUpdates(ImageContentBlockEntity entity, ImageUpdateRequest updateRequest) {
        // Update basic image metadata fields if provided
        if (updateRequest.getTitle() != null) {
            entity.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getRating() != null) {
            entity.setRating(updateRequest.getRating());
        }
        if (updateRequest.getLocation() != null) {
            entity.setLocation(updateRequest.getLocation());
        }
        if (updateRequest.getAuthor() != null) {
            entity.setAuthor(updateRequest.getAuthor());
        }
        if (updateRequest.getIsFilm() != null) {
            entity.setIsFilm(updateRequest.getIsFilm());
        }
        // Handle film type - create new or fetch existing by ID
        // newFilmType takes precedence over filmTypeId if both are provided
        if (updateRequest.getNewFilmType() != null) {
            // Create new film type or find existing one
            String displayName = updateRequest.getNewFilmType().getFilmTypeName().trim();
            // Generate technical name from display name: "Kodak Portra 400" -> "KODAK_PORTRA_400"
            String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");

            ContentFilmTypeEntity filmType = contentFilmTypeRepository
                    .findByFilmTypeNameIgnoreCase(technicalName)
                    .orElseGet(() -> {
                        log.info("Creating new film type: {} (technical name: {})", displayName, technicalName);
                        ContentFilmTypeEntity newFilmType = new ContentFilmTypeEntity(
                                technicalName,
                                displayName,
                                updateRequest.getNewFilmType().getDefaultIso()
                        );
                        return contentFilmTypeRepository.save(newFilmType);
                    });
            entity.setFilmType(filmType);
        } else if (updateRequest.getFilmTypeId() != null) {
            // Use existing film type by ID
            ContentFilmTypeEntity filmType = contentFilmTypeRepository.findById(updateRequest.getFilmTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Film type not found with ID: " + updateRequest.getFilmTypeId()));
            entity.setFilmType(filmType);
        }
        if (updateRequest.getFilmFormat() != null) {
            entity.setFilmFormat(updateRequest.getFilmFormat());
        }

        // Validate: if isFilm is being set to true in the update request,
        // filmFormat must also be provided in the update request (or already exist on the entity)
        if (updateRequest.getIsFilm() != null && updateRequest.getIsFilm()) {
            // Check if filmFormat will be set after this update
            if (entity.getFilmFormat() == null && updateRequest.getFilmFormat() == null) {
                throw new IllegalArgumentException("filmFormat is required when isFilm is true");
            }
        }

        if (updateRequest.getBlackAndWhite() != null) {
            entity.setBlackAndWhite(updateRequest.getBlackAndWhite());
        }

        // Handle camera - cameraId takes precedence over cameraName
        if (updateRequest.getCameraId() != null) {
            // Use existing camera by ID
            ContentCameraEntity camera = contentCameraRepository.findById(updateRequest.getCameraId())
                    .orElseThrow(() -> new IllegalArgumentException("Camera not found with ID: " + updateRequest.getCameraId()));
            entity.setCamera(camera);
        } else if (updateRequest.getCameraName() != null && !updateRequest.getCameraName().trim().isEmpty()) {
            // Find existing or create new camera by name
            String cameraName = updateRequest.getCameraName().trim();
            ContentCameraEntity camera = contentCameraRepository.findByCameraNameIgnoreCase(cameraName)
                    .orElseGet(() -> {
                        log.info("Creating new camera: {}", cameraName);
                        ContentCameraEntity newCamera = new ContentCameraEntity(cameraName);
                        return contentCameraRepository.save(newCamera);
                    });
            entity.setCamera(camera);
        }

        // Handle lens - lensId takes precedence over lensName
        if (updateRequest.getLensId() != null) {
            // Use existing lens by ID
            ContentLensEntity lens = contentLensRepository.findById(updateRequest.getLensId())
                    .orElseThrow(() -> new IllegalArgumentException("Lens not found with ID: " + updateRequest.getLensId()));
            entity.setLens(lens);
        } else if (updateRequest.getLensName() != null && !updateRequest.getLensName().trim().isEmpty()) {
            // Find existing or create new lens by name
            String lensName = updateRequest.getLensName().trim();
            ContentLensEntity lens = contentLensRepository.findByLensNameIgnoreCase(lensName)
                    .orElseGet(() -> {
                        log.info("Creating new lens: {}", lensName);
                        ContentLensEntity newLens = new ContentLensEntity(lensName);
                        return contentLensRepository.save(newLens);
                    });
            entity.setLens(lens);
        }
        if (updateRequest.getFocalLength() != null) {
            entity.setFocalLength(updateRequest.getFocalLength());
        }
        if (updateRequest.getFStop() != null) {
            entity.setFStop(updateRequest.getFStop());
        }
        if (updateRequest.getShutterSpeed() != null) {
            entity.setShutterSpeed(updateRequest.getShutterSpeed());
        }
        if (updateRequest.getIso() != null) {
            entity.setIso(updateRequest.getIso());
        }
        if (updateRequest.getCreateDate() != null) {
            entity.setCreateDate(updateRequest.getCreateDate());
        }

        // Note: Tag and person relationship updates are handled separately in the service layer
        // Collection visibility updates are also handled separately in handleCollectionVisibilityUpdates
    }

    /**
     * Handle collection visibility and orderIndex updates for an image.
     * This method updates the 'visible' flag and 'orderIndex' for the content_block entry in the current collection.
     * Note: For cross-collection updates (updating the same image in multiple collections),
     * you would need to add a repository method to find blocks by fileIdentifier.
     * For now, this handles visibility and orderIndex for the current image/collection relationship.
     *
     * @param image The image entity being updated
     * @param collectionUpdates List of collection updates containing visibility and orderIndex information
     */
    public void handleCollectionVisibilityUpdates(ImageContentBlockEntity image, List<ImageCollection> collectionUpdates) {
        if (collectionUpdates == null || collectionUpdates.isEmpty()) {
            return;
        }

        // Update visibility and orderIndex for the current image if its collection is in the updates
        for (ImageCollection collectionUpdate : collectionUpdates) {
            if (collectionUpdate.getCollectionId() != null &&
                collectionUpdate.getCollectionId().equals(image.getCollectionId())) {

                boolean updated = false;

                // Update visible if provided
                if (collectionUpdate.getVisible() != null) {
                    image.setVisible(collectionUpdate.getVisible());
                    log.info("Updated visibility for image {} in collection {} to {}",
                            image.getId(), image.getCollectionId(), collectionUpdate.getVisible());
                    updated = true;
                }

                // Update orderIndex if provided
                if (collectionUpdate.getOrderIndex() != null) {
                    image.setOrderIndex(collectionUpdate.getOrderIndex());
                    log.info("Updated orderIndex for image {} in collection {} to {}",
                            image.getId(), image.getCollectionId(), collectionUpdate.getOrderIndex());
                    updated = true;
                }

                if (updated) {
                    break; // Only update once for the matching collection
                }
            }
        }
    }
}
