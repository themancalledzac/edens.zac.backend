package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.CodeContentBlockModel;
import edens.zac.portfolio.backend.model.ContentBlockModel;
import edens.zac.portfolio.backend.model.GifContentBlockModel;
import edens.zac.portfolio.backend.model.ImageContentBlockModel;
import edens.zac.portfolio.backend.model.TextContentBlockModel;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
import edens.zac.portfolio.backend.types.ContentBlockType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    // Dependency on ImageProcessingUtil for image-specific processing
    private final ImageProcessingUtil imageProcessingUtil;

    // Dependencies for S3 upload and content block repository
    private final AmazonS3 amazonS3;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentCollectionRepository contentCollectionRepository;

    @Value("${aws.portfolio.s3.bucket}")
    private String bucketName;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

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
     * @param model The target model
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
        model.setLens(entity.getLens());
        model.setBlackAndWhite(entity.getBlackAndWhite());
        model.setIsFilm(entity.getIsFilm());
        model.setShutterSpeed(entity.getShutterSpeed());
        model.setRawFileName(entity.getRawFileName());
        model.setCamera(entity.getCamera());
        model.setFocalLength(entity.getFocalLength());
        model.setLocation(entity.getLocation());
        model.setImageUrlWeb(entity.getImageUrlWeb());
        model.setCreateDate(entity.getCreateDate());

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

        return model;
    }

    /**
     * Process and save a content block based on its type.
     * 
     * @param file The file to process (for media content blocks)
     * @param type The type of content block
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex The order index of this block within the collection
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
     * Process and save an image content block.
     * 
     * @param file The image file to process
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex The order index of this block within the collection
     * @param title The title of the image
     * @param caption The caption for the image
     * @return The saved image content block entity
     */
    public ImageContentBlockEntity processImageContentBlock(
            MultipartFile file,
            Long collectionId,
            Integer orderIndex,
            String title,
            String caption
    ) {
        log.info("Processing image content block for collection {}", collectionId);

        try {
            // Fetch the collection to get its location
            String collectionLocation = contentCollectionRepository.findById(collectionId)
                    .map(ContentCollectionEntity::getLocation)
                    .orElse("");

            // Use ImageProcessingUtil to process the image and upload to S3
            // Use "content_collection" as the type and the collection ID as the context name
            ImageEntity imageEntity = imageProcessingUtil.processAndSaveImage(file, "content_collection", collectionId.toString(), collectionLocation);

            if (imageEntity == null) {
                log.error("Failed to process image - ImageProcessingUtil returned null");
                return null;
            }

            // Create and return the image content block entity using builder pattern
            ImageContentBlockEntity entity = ImageContentBlockEntity.builder()
                    .collectionId(collectionId)
                    .orderIndex(orderIndex)
                    .blockType(ContentBlockType.IMAGE)
                    .caption(caption)
                    .title(title != null ? title : imageEntity.getTitle())
                    .imageWidth(imageEntity.getImageWidth())
                    .imageHeight(imageEntity.getImageHeight())
                    .iso(imageEntity.getIso())
                    .author(imageEntity.getAuthor())
                    .rating(imageEntity.getRating())
                    .fStop(imageEntity.getFStop())
                    .lens(imageEntity.getLens())
                    .blackAndWhite(imageEntity.getBlackAndWhite())
                    .isFilm(imageEntity.getIsFilm()) // Use isFilm from ImageEntity instead of hardcoded false
                    .shutterSpeed(imageEntity.getShutterSpeed())
                    .rawFileName(imageEntity.getRawFileName())
                    .camera(imageEntity.getCamera())
                    .focalLength(imageEntity.getFocalLength())
                    .location(imageEntity.getLocation())
                    .imageUrlWeb(imageEntity.getImageUrlWeb())
                    .createDate(imageEntity.getCreateDate())
                    .build();

            // Save the entity using the repository
            return contentBlockRepository.save(entity);

        } catch (Exception e) {
            log.error("Error processing image content block: {}", e.getMessage(), e);
            return null; // Return null instead of throwing exception
        }
    }

    /**
     * Process and save a text content block.
     * 
     * @param text The text content
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex The order index of this block within the collection
     * @param caption The caption for the text block
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
     * @param code The code content
     * @param language The programming language
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex The order index of this block within the collection
     * @param title The title of the code block
     * @param caption The caption for the code block
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
     * @param file The gif file to process
     * @param collectionId The ID of the collection this block belongs to
     * @param orderIndex The order index of this block within the collection
     * @param title The title of the gif
     * @param caption The caption for the gif
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
     * @param blockIds The ordered list of block IDs
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
     * @param content The code content to validate
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
     * @param date The date to use in the key
     * @param filename The filename
     * @param type The type of file (gif, thumbnail, etc.)
     * @return The generated S3 key
     */
    private String generateS3Key(String date, String filename, String type) {
        return String.format("%s/%s/%s", date, type, filename);
    }
}
