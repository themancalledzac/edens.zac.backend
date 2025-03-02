package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class ImageProcessingUtil {


    private final AmazonS3 amazonS3;
    private final String bucketName;
    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;

    @Autowired
    public ImageProcessingUtil(
            AmazonS3 amazonS3,
            @Value("${aws.s3.bucket}") String bucketName,
            ImageRepository imageRepository,
            CatalogRepository catalogRepository) {
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
    }

    @PostConstruct
    public void logConfig() {
        log.info("ImageProcessingUtil initialized");
        log.info("Actual bucket name value: '{}'", bucketName);
        log.info("AWS_PORTFOLIO_S3_BUCKET env var: '{}'", System.getenv("AWS_BUCKET_NAME"));
    }

    /**
     * Complete process for handling an image. extract metadata, upload to S3, and save to database
     *
     * @param file        The image file to process
     * @param type        The collection name ("blog" or "catalog") where this image belongs
     * @param contextName The title of the Blog or Catalog
     * @return The created ImageEntity
     */
    public ImageEntity processAndSaveImage(MultipartFile file, String type, String contextName) {
        try {

            // Step 1: Extract Metadata
            Map<String, String> imageMetadata = extractImageMetadata(file);

            // Step 2: Upload to S3
            String s3Url = uploadImageToS3(file, imageMetadata.get("date"));

            // Step 3: Create and save Image entity
            ImageEntity imageEntity = buildImageEntity(imageMetadata, s3Url, type, contextName);


            // Step 4: Check for existing Image
            Optional<ImageEntity> existingImage = imageRepository.findByTitleAndCreateDate(
                    imageEntity.getTitle(),
                    imageEntity.getCreateDate());

            if (existingImage.isPresent()) {
                log.info("Image already exists: {}", imageEntity.getTitle());
                ImageEntity existingImageEntity = existingImage.get();

                // Update catalogs on existing image if needed
                if (type.equals("catalog")) {
                    CatalogEntity catalog = getCatalogEntity(contextName);
                    existingImage.get().getCatalogs().add(catalog);
                }
                // TODO: What is this issue with 'save'? do we need a custom method?
                return imageRepository.save(existingImage.get());
            }

            // Step 5: Save and return
            log.info("Saving new image to database: {}", imageEntity.getTitle());
            return imageRepository.save(imageEntity);
        } catch (Exception e) {
            log.error("Error processing image file", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extract metadata from an image file
     */
    public Map<String, String> extractImageMetadata(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) { // TODO: Why is this 'inside' the param block, and why does a try block have a param block?
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata(metadata);
            Map<String, String> imageMetadata = new HashMap<>();

            // Extract basic metadata
            imageMetadata.put("title", file.getOriginalFilename());
            imageMetadata.put("imageWidth", extractValueForKey(directoriesList, "Image Width"));
            imageMetadata.put("imageHeight", extractValueForKey(directoriesList, "Image Height"));
            imageMetadata.put("iso", extractValueForKey(directoriesList, "ISO Speed Ratings"));
            imageMetadata.put("author", extractValueForKey(directoriesList, "Artist"));
            imageMetadata.put("rating", extractValueForKey(directoriesList, "xmp:Rating"));
            imageMetadata.put("focalLength", extractValueForKey(directoriesList, "Focal Length 35"));
            imageMetadata.put("fStop", extractValueForKey(directoriesList, "F-Number"));
            imageMetadata.put("shutterSpeed", extractValueForKey(directoriesList, "Shutter Speed Value"));
            imageMetadata.put("lens", extractValueForKey(directoriesList, "Lens Model"));
            imageMetadata.put("lensSpecific", extractValueForKey(directoriesList, "Lens Specification"));
            imageMetadata.put("camera", extractValueForKey(directoriesList, "Model"));
            imageMetadata.put("date", extractValueForKey(directoriesList, "Date/Time Original"));
            imageMetadata.put("blackAndWhite", String.valueOf(Objects.equals(extractValueForKey(directoriesList, "crs:ConvertToGrayscale"), "True")));
            imageMetadata.put("rawFileName", extractValueForKey(directoriesList, "crs:RawFileName"));

            return imageMetadata;

        } catch (Exception e) {
            log.error("Error extracting image metadata: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract image metadata: " + e.getMessage(), e);
        }
    }

    public String uploadImageToS3(MultipartFile file, String date) {
        try (InputStream inputStream = file.getInputStream()) {
            // Generate S3 Key
            String webS3Key = generateS3Key(date, file.getOriginalFilename(), ImageType.WEB);

            log.info("Uploading to S3 bucket: {}", bucketName);

            // Set metadata
            ObjectMetadata webMetadata = new ObjectMetadata();
            webMetadata.setContentType(file.getContentType());
            webMetadata.setContentLength(file.getSize());

            // Upload with public read permissions
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    webS3Key,
                    inputStream,
                    webMetadata
            );

            amazonS3.putObject(putObjectRequest);

            // Return the URL
            String url = amazonS3.getUrl(bucketName, webS3Key).toString();
            log.info("File uploaded successfully to S3. URL: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Error uploading image to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }

    public ImageEntity buildImageEntity(Map<String, String> imageMetadata, String s3Url, String type, String contextName) {
        Set<CatalogEntity> catalogs = new HashSet<>();

        // Determine which catalog to add based on the type
        if ("catalog".equals(type)) {
            CatalogEntity catalog = getCatalogEntity(contextName);
            catalogs.add(catalog);
        }

        // Build the image entity
        return ImageEntity.builder()
                .title(imageMetadata.get("title"))
                .imageWidth(parseIntegerOrDefault(imageMetadata.get("imageWidth"), 0))
                .imageHeight(parseIntegerOrDefault(imageMetadata.get("imageHeight"), 0))
                .iso(parseIntegerOrDefault(imageMetadata.get("iso"), 0))
                .author(imageMetadata.get("author"))
                .rating(parseIntegerOrDefault(imageMetadata.get("rating"), 0))
                .fStop(imageMetadata.get("fStop"))
                .lens(imageMetadata.get("lens"))
                .blackAndWhite(Boolean.parseBoolean(imageMetadata.get("blackAndWhite")))
                .shutterSpeed(imageMetadata.get("shutterSpeed"))
                .rawFileName(imageMetadata.get("rawFileName"))
                .camera(imageMetadata.get("camera"))
                .focalLength(imageMetadata.get("focalLength"))
                .location(contextName + "/" + imageMetadata.get("title"))
                .imageUrlWeb(s3Url)
                .imageUrlSmall(s3Url) // For now, same URL
                .imageUrlRaw(null)
                .createDate(imageMetadata.get("date"))
                .updateDate(LocalDateTime.now())
                .catalogs(catalogs)
                .build();
    }

    ImageModel convertImageEntityToImageModel(ImageEntity imageEntity) {

        ImageModel imageModel = new ImageModel();
        imageModel.setId(imageEntity.getId());
        imageModel.setTitle(imageEntity.getTitle());
        imageModel.setImageWidth(imageEntity.getImageWidth());
        imageModel.setImageHeight(imageEntity.getImageHeight());
        imageModel.setIso(imageEntity.getIso());
        imageModel.setAuthor(imageEntity.getAuthor());
        imageModel.setRating(imageEntity.getRating());
        imageModel.setFStop(imageEntity.getFStop());
        imageModel.setLens(imageEntity.getLens());
        imageModel.setBlackAndWhite(imageEntity.getBlackAndWhite());
        imageModel.setShutterSpeed(imageEntity.getShutterSpeed());
        imageModel.setRawFileName(imageEntity.getRawFileName());
        imageModel.setCamera(imageEntity.getCamera());
        imageModel.setFocalLength(imageEntity.getFocalLength());
        imageModel.setLocation(imageEntity.getLocation());
        imageModel.setImageUrlWeb(imageEntity.getImageUrlWeb());
        imageModel.setImageUrlSmall(imageEntity.getImageUrlSmall());
        imageModel.setImageUrlRaw(imageEntity.getImageUrlRaw());
        imageModel.setCreateDate(imageEntity.getCreateDate());
        imageModel.setUpdateDate(imageEntity.getUpdateDate());
        imageModel.setCatalog(imageEntity.getCatalogNames());
        imageModel.setBlogs(imageEntity.getBlogTitles());

        return imageModel;
    }

    /**
     * Collect all directories of metadata for an image
     *
     * @param metadata Metadata
     * @return Returns the directories List
     */
    public List<Map<String, Object>> collectAllDirectoriesMetadata(Metadata metadata) {
        List<Map<String, Object>> directoriesList = new ArrayList<>();

        for (Directory directory : metadata.getDirectories()) {
            Map<String, Object> directoryData = new HashMap<>();
            Map<String, String> tagMap = new HashMap<>();

            for (Tag tag : directory.getTags()) {
                tagMap.put(tag.getTagName(), tag.getDescription());
            }

            // Special handling for XMP Directory
            if (directory instanceof XmpDirectory xmpDirectory) {
                Map<String, String> xmpProperties = xmpDirectory.getXmpProperties();
                if (xmpProperties != null) {
                    // Prefix XMP properties to distinguish them from standard tags
                    tagMap.putAll(xmpProperties);
                }
            }

            directoryData.put(directory.getClass().getSimpleName(), tagMap);
            directoriesList.add(directoryData);
        }
        return directoriesList;
    }

    public static String extractValueForKey(List<Map<String, Object>> directoriesList, String targetKey) {
        // loop through each directory
        for (Map<String, Object> directoryData : directoriesList) {

            // each key in directoryData map is the directoryname, and the value is the metadata map
            for (Map.Entry<String, Object> entry : directoryData.entrySet()) {

                // cast the value to Map<String, String> as we know it's the structure of our metadata
                Map<String, String> metadata = (Map<String, String>) entry.getValue();

                // check if this metadata map contains the target key
                if (metadata.containsKey(targetKey)) {
                    return metadata.get(targetKey);
                }
            }
//            Map<String, String> metdata = (Map<String, String>) directoryData.get("metadata")
        }
        return null;
    }

    /**
     * Creates our S3 Key, which is essential for the structure of our image's (folders) in S3
     *
     * @param date     Date of Image
     * @param filename Image filename, usually something like '_DSC2452.webp'
     * @param type     Image type such as 'web'
     * @return S3 Key
     */
    private String generateS3Key(String date, String filename, ImageType type) {
        String formattedDate = date.split(" ")[0].replace(':', '-');
        return String.format("%s/%s/%s",
                formattedDate,
                type,
                filename);
    }

    @Getter
    private enum ImageType {
        WEB("web"),
        THUMBNAIL("thumbnail");

        private final String folderName;

        ImageType(String folderName) {
            this.folderName = folderName;
        }

    }

    /**
     * Get or create a catalog entity
     */
    private CatalogEntity getCatalogEntity(String catalogName) {
        return catalogRepository.findByName(catalogName)
                .orElseGet(() -> catalogRepository.save(new CatalogEntity(catalogName)));
    }

    /**
     * Parse integer from string with default value
     */
    private Integer parseIntegerOrDefault(String value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

//    public String uploadImageToS3()
}
