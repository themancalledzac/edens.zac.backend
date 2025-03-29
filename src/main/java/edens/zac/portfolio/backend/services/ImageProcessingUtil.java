package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
            @Value("${aws.portfolio.s3.bucket}") String bucketName,
            ImageRepository imageRepository,
            CatalogRepository catalogRepository) {
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
    }

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

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

            // Step 2: Compress the image while preserving metadata
            // Define your desired path for compression
            int maxDimension = 2000; // Max dimension of long side
            float compressionQuality = 0.85f; // Compression quality (0.0 -> 1.0)

            ByteArrayInputStream compressedImageStream = compressImage(file, imageMetadata, maxDimension, compressionQuality);

            // Step 3: Upload the compressed image to S3

            String contentType = file.getContentType();
            Long fileSize = file.getSize();
            String s3Url = uploadImageToS3(
                    compressedImageStream,
                    imageMetadata.get("date"),
                    file.getOriginalFilename(),
                    contentType,
                    fileSize
            );
//            String imageDate = imageMetadata.get("date").isEmpty() ? imageMetadata.get("date") : "";
//            String s3Url = uploadImageToS3(file, imageDate);

            // Step 4: Create and save Image entity
            ImageEntity imageEntity = buildImageEntity(imageMetadata, s3Url, type, contextName);


            // Step 5: Check for existing Image
            Optional<ImageEntity> existingImage = imageRepository.findByTitleAndCreateDate(
                    imageEntity.getTitle(),
                    imageEntity.getCreateDate());

            if (existingImage.isPresent()) {
                log.info("Image already exists: {}", imageEntity.getTitle());

                // Update catalogs on existing image if needed
                if (type.equals("catalog")) {
                    CatalogEntity catalog = getCatalogEntity(contextName);
                    existingImage.get().getCatalogs().add(catalog);
                }
                // TODO: What is this issue with 'save'? do we need a custom method?
                return imageRepository.save(existingImage.get());
            }

            // Step 6: Save and return
            log.info("Saving new image to database: {}", imageEntity.getTitle());
            return imageRepository.save(imageEntity);
        } catch (Exception e) {
            log.error("Error processing image file: {}", e.getMessage(), e);
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

    public ByteArrayInputStream compressImage(MultipartFile file, Map<String, String> imageMetadata, int maxDimension, float compressionQuality) throws IOException {
        // Read original image
        BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
        if (bufferedImage == null) {
            throw new IOException("Failed to read image: " + file.getOriginalFilename());
        }

        // Get original dimensions
        int originalWidth = parseIntegerOrDefault(imageMetadata.get("imageWidth"), 0);
        int originalHeight = parseIntegerOrDefault(imageMetadata.get("imageHeight"), 0);

        // Calculate new dimensions with aspect ratio
        int newWidth, newHeight;
        if (originalWidth > originalHeight) {
            if (originalWidth > maxDimension) {
                newWidth = maxDimension;
                newHeight = (int) (originalHeight * (((double) maxDimension / originalWidth)));
            } else {
                newWidth = originalWidth;
                newHeight = originalHeight;
            }
        } else {
            if (originalHeight > maxDimension) {
                newHeight = maxDimension;
                newWidth = (int) (originalWidth * (((double) maxDimension / originalHeight)));
            } else {
                newHeight = originalHeight;
                newWidth = originalWidth;
            }
        }

        // Skip resize if image is already smaller than the max dimension
        BufferedImage resizedImage;
        if (newWidth != originalWidth || newHeight != originalHeight) {
            // create a new resized image
            resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(bufferedImage, 0, 0, newWidth, newHeight, null);
            g.dispose();
        } else {
            resizedImage = bufferedImage;
        }

        // Compress image
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Get the image format
        String formatName = getImageFormat(file.getOriginalFilename());

        // For jpg/jpeg, use compression
        if ("jpg".equalsIgnoreCase(formatName) || "jpeg".equalsIgnoreCase(formatName)) {
            Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByFormatName(formatName);
            if (!imageWriters.hasNext()) {
                throw new IOException("Unsupported image format: " + formatName);
            }

            ImageWriter writer = imageWriters.next();
            ImageWriteParam writeParam = writer.getDefaultWriteParam();

            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(compressionQuality);

            ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(resizedImage, null, null), writeParam);
            writer.dispose();
            ios.close();
        }

        // For WebP format
        else if ("webp".equalsIgnoreCase(formatName)) {
            Thumbnails.of(resizedImage)
                    .size(newWidth, newHeight)
                    .outputQuality(compressionQuality)
                    .outputFormat("webp")
                    .toOutputStream(outputStream);
        }

        // For all other formats
        else {
            ImageIO.write(resizedImage, formatName, outputStream);
        }

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private String getImageFormat(String filename) {
        if (filename == null) return "jpg";

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "jpg"; // default if no extension found
    }

    private String uploadImageToS3(
            ByteArrayInputStream file,
            String date,
            String originalFilename,
            String contentType,
            Long fileSize
    ) throws IOException {

        // Read available bytes from the input stream
        byte[] bytes = file.readAllBytes();
        // Create a new input stream
        ByteArrayInputStream resetStream = new ByteArrayInputStream(bytes);

        // Default date to current date in YYYY-MM-DD format if null
        String uploadDate = date;
        if (date == null || date.isEmpty()) {
            uploadDate = java.time.LocalDate.now().toString();
            log.info("No date metadata found - using current date: {}", uploadDate);
        } else {
            log.info("Using provided date metadata: {}", date);
        }

        // Generate S3 Key
        String webS3Key = generateS3Key(date, originalFilename, ImageType.WEB);

        log.info("Uploading to S3 bucket: {}", bucketName);

        // Set metadata
        ObjectMetadata webMetadata = new ObjectMetadata();
        webMetadata.setContentType(contentType);
        webMetadata.setContentLength(bytes.length);

        // Upload with public read permissions
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName,
                webS3Key,
                resetStream,
                webMetadata
        );

        amazonS3.putObject(putObjectRequest);

        String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + webS3Key;

        // Return the URL
        // String url = amazonS3.getUrl(bucketName, webS3Key).toString();
        log.info("File uploaded successfully to S3. URL: {}", cloudfrontUrl);
        log.info("=============== UPLOAD COMPLETE ===============");
        return cloudfrontUrl;

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
                .blogs(new HashSet<>())
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

        // Format URLs with CloudFront if needed
        imageModel.setImageUrlWeb(formatImageUrl(imageEntity.getImageUrlWeb()));
        imageModel.setImageUrlSmall(formatImageUrl(imageEntity.getImageUrlSmall()));
        imageModel.setImageUrlRaw(formatImageUrl(imageEntity.getImageUrlRaw()));
        imageModel.setCreateDate(imageEntity.getCreateDate());
        imageModel.setUpdateDate(imageEntity.getUpdateDate());
        imageModel.setCatalog(imageEntity.getCatalogNames());
        imageModel.setBlogs(imageEntity.getBlogTitles());

        return imageModel;
    }

    /**
     * Ensures image URL is properly formatted with CloudFront domain if needed
     *
     * @param url The S3 or CloudFront URL
     * @return Properly formatted URL
     */
    private String formatImageUrl(String url) {
        if (url == null) {
            return null;
        }

        // If URL already has CloudFront domain, return as is
        if (url.contains(cloudfrontDomain)) {
            return url;
        }

        // If it's an S3 URL, convert to CloudFront
        if (url.contains(bucketName + ".s3")) {
            // Extract the object key from the S3 URL
            String objectKey = url.substring(url.indexOf(bucketName) + bucketName.length() + 1);
            return "https://" + cloudfrontDomain + "/" + objectKey;
        }

        // Return original URL if it doesn't need modification
        return url;
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
        log.info("Generating S3 key with date: '{}', filename: '{}', type: '{}'", date, filename, type);
        String formattedDate;
        if (date == null || date.isEmpty()) {
            formattedDate = LocalDate.now().toString();
            log.info("Using curent date: {}", formattedDate);
        } else {
            try {
                // try to extract date
                String[] parts = date.split(" ");
                formattedDate = parts[0].replace(':', '-');
                log.info("Formatted date for S3 key: {}", formattedDate);
            } catch (Exception e) {
                log.warn("Could not parse date '{}', using current date instead. Error: {}", date, e.getMessage());
                formattedDate = LocalDate.now().toString();
            }
        }

//        String formattedDate = date.split(" ")[0].replace(':', '-');
        String s3Key = String.format("%s/%s/%s", formattedDate, type, filename);
        log.info("Generated S3 key: {}", s3Key);
        return s3Key;
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
    private CatalogEntity getCatalogEntity(String catalogTitle) {
        return catalogRepository.findByTitle(catalogTitle)
                .orElseGet(() -> {
                    CatalogEntity catalogEntity = CatalogEntity.builder()
                            .title(catalogTitle)
                            .date(LocalDate.now())
                            .build();
                    return catalogRepository.save(catalogEntity);
                });
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

    public String generateSlug(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }

        return title.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s-]", "") // Remove all non-alphanumeric chars except space and -
                .replaceAll("\\s+", "-") // Replace spaces with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with single hyphen
                .replaceAll("^-|-$", ""); // Remove leading and trailing hyphens
    }
}
