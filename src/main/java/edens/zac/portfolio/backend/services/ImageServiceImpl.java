package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogImagesDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;
    //    private final ImageProcessingService imageProcessingService;
    private final AmazonS3 amazonS3;
    private final String bucketName;

    @Autowired
    public ImageServiceImpl(
            ImageRepository imageRepository,
            CatalogRepository catalogRepository,
            AmazonS3 amazonS3,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;

        log.info("ImageServiceImpl initialized");
        log.info("Bucket name: {}", bucketName);
    }

    // TODO: Update this postImages so we:
    //  1. Take an Object of data in addition to each image file
    //  2. If key doesn't exist in object, replace with our old route
    //  3. Update to include Catalog logic (object should now CONTAIN our catalog list, upload each that does not already exist )
    //  4. Upload Image to S3, get back URL
    //  5. Remove thumbnail logic for now? Look at replacing it with Dan's comment of 'on getImage' use lamda to return thumbnail if requested
    //  6. database upload for image and any catalog changes
    //  7. return a List of objects for each image, where it contains:
    //   a. Image upload success / error message, database upload success / error message, Image location(s3 url), ?
    @Override
    @Transactional
    public Map<String, String> postImages(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata(metadata);
            Map<String, String> imageReturnMetadata = new HashMap<>();
            imageReturnMetadata.put("title", file.getOriginalFilename());
            imageReturnMetadata.put("imageWidth", extractValueForKey(directoriesList, "Image Width"));
            imageReturnMetadata.put("imageHeight", extractValueForKey(directoriesList, "Image Height"));
            imageReturnMetadata.put("iso", extractValueForKey(directoriesList, "ISO Speed Ratings"));
            imageReturnMetadata.put("author", extractValueForKey(directoriesList, "Artist"));
            imageReturnMetadata.put("rating", extractValueForKey(directoriesList, "xmp:Rating"));
            imageReturnMetadata.put("focalLength", extractValueForKey(directoriesList, "Focal Length 35"));
            imageReturnMetadata.put("fStop", extractValueForKey(directoriesList, "F-Number"));
            imageReturnMetadata.put("shutterSpeed", extractValueForKey(directoriesList, "Shutter Speed Value"));
            imageReturnMetadata.put("lens", extractValueForKey(directoriesList, "Lens Model"));
            imageReturnMetadata.put("lensSpecific", extractValueForKey(directoriesList, "Lens Specification"));
            imageReturnMetadata.put("camera", extractValueForKey(directoriesList, "Model"));
            imageReturnMetadata.put("date", extractValueForKey(directoriesList, "Date/Time Original"));
            imageReturnMetadata.put("blackAndWhite", String.valueOf(Objects.equals(extractValueForKey(directoriesList, "crs:ConvertToGrayscale"), "True")));
            imageReturnMetadata.put("rawFileName", extractValueForKey(directoriesList, "crs:RawFileName"));

            // Generate S3 key for both image sizes
            String webS3Key = generateS3Key(file.getOriginalFilename(), ImageType.WEB);
            String thumbnailS3Key = generateS3Key(file.getOriginalFilename(), ImageType.THUMBNAIL);

            // Upload original image size to web folder
            ObjectMetadata webMetadata = new ObjectMetadata();
            webMetadata.setContentType(file.getContentType());
            webMetadata.setContentLength(file.getSize());

            amazonS3.putObject(new PutObjectRequest(
                    bucketName,
                    webS3Key,
                    file.getInputStream(),
                    webMetadata
            ));

//            byte[] thumbnailBytes = imageProcessingService.createThumbnail(file);
//            ObjectMetadata thumbnailMetadata = new ObjectMetadata();
//            thumbnailMetadata.setContentType("image/webp");
//            thumbnailMetadata.setContentLength(thumbnailBytes.length);
//
//            amazonS3.putObject(new PutObjectRequest(
//                    bucketName,
//                    thumbnailS3Key,
//                    new ByteArrayInputStream(thumbnailBytes),
//                    thumbnailMetadata
//            ));

            // Get URLs for both versions
            String webUrl = amazonS3.getUrl(bucketName, webS3Key).toString();
            String thumbnailUrl = amazonS3.getUrl(bucketName, thumbnailS3Key).toString();


            // SHORT TERM solution for adding catalogs.
            // Will need to get some sort of UI or otherwise to add these further down the road.
            List<String> catalogNames = new ArrayList<>();
            catalogNames.add("Vienna");
//            catalogNames.add("Adventure");

            ImageEntity builtImage = ImageEntity.builder()
                    .title(file.getOriginalFilename())
                    .imageWidth(parseIntegerOrDefault(extractValueForKey(directoriesList, "Image Width"), 0))
                    .imageHeight(parseIntegerOrDefault(extractValueForKey(directoriesList, "Image Height"), 0))
                    .iso(Integer.valueOf(Objects.requireNonNull(extractValueForKey(directoriesList, "ISO Speed Ratings"))))
                    .author(extractValueForKey(directoriesList, "Artist"))
                    .rating(Integer.valueOf(Objects.requireNonNull(extractValueForKey(directoriesList, "xmp:Rating"))))
                    .fStop(extractValueForKey(directoriesList, "F-Number"))
                    .lens(extractValueForKey(directoriesList, "Lens Model"))
                    .blackAndWhite("True".equalsIgnoreCase(extractValueForKey(directoriesList, "crs:ConvertToGrayscale")))
                    .shutterSpeed(extractValueForKey(directoriesList, "Shutter Speed Value"))
                    .rawFileName(extractValueForKey(directoriesList, "crs:RawFileName"))
                    .camera(extractValueForKey(directoriesList, "Model"))
                    .focalLength(extractValueForKey(directoriesList, "Focal Length 35"))
                    .location(catalogNames.get(0) + "/" + file.getOriginalFilename())
                    .imageUrlWeb(webUrl)
                    .imageUrlSmall(thumbnailUrl)
                    .imageUrlRaw(null)
                    .createDate(extractValueForKey(directoriesList, "Date/Time Original"))
                    .updateDate(LocalDateTime.now())
                    .build();

            Optional<ImageEntity> existingImage = imageRepository.findByTitleAndCreateDate(
                    builtImage.getTitle(), builtImage.getCreateDate()
            );
            Set<CatalogEntity> catalogs = new HashSet<>();

            // if catalog exists in db, don't add, otherwise do!
            for (String catalogName : catalogNames) {
                CatalogEntity catalog = catalogRepository.findByName(catalogName).orElseGet(() -> catalogRepository.save(new CatalogEntity(catalogName)));
                catalogs.add(catalog);
            }
            // if image does not yet exist, add catalogs, create image.
            if (existingImage.isEmpty()) {
                builtImage.setCatalogs(catalogs);
                imageRepository.save(builtImage);

            }
            return imageReturnMetadata;
        } catch (DataIntegrityViolationException e) {
            log.error("Database integrity error while processing image", e);
            throw new DataIntegrityViolationException("Failed to save image metadata: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO error while processing image", e);
            throw new RuntimeException("Failed to read or write image file: " + e.getMessage(), e);
        } catch (ImageProcessingException e) {
            log.error("Error processing image metadata", e);
            throw new RuntimeException("Failed to extract image metadata: " + e.getMessage(), e);
        } catch (AmazonS3Exception e) {  // Add this for S3 errors
            log.error("S3 upload error", e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage(), e);
        }

    }

    @Override
    @Transactional(readOnly = true) // use readOnly for fetch operations
    public ImageModel getImageById(Long imageId) {

        Optional<ImageEntity> imageOpt = imageRepository.findByIdWithCatalogs(imageId); // required type UUID, provided: Long

        return imageOpt.map(this::convertToModalImage).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageModel> getAllImagesByCatalog(String catalogTitle) {

        Set<Long> imageIds = imageRepository.findImageIdsByCatalogName(catalogTitle);
        Set<Long> imageHeaders = imageRepository.findImageIdsByCatalogNameAndCriteria(catalogTitle);

        // Corrected stream operation to handle Optional correctly
        List<ImageEntity> images = imageIds.stream()
                .map(imageRepository::findByIdWithCatalogs) // This returns Stream<Optional<Image>> // OLD::=> .map(id -> imageRepository.findByIdWithCatalogs(id))
                .filter(Optional::isPresent) // Filter to only present Optionals
                .map(Optional::get) // Extract Image from Optional
                .toList(); // Collect to List<Image>

//        List<Image> images = imageRepository.findByCatalogName(catalogTitle);
        return images.stream()
                .map(this::convertToModalImage)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogImagesDTO> getAllImagesByCatalog(List<String> catalogTitles) {

        List<CatalogImagesDTO> results = new ArrayList<>();

        for (String title : catalogTitles) {

            Set<Long> imageIds = imageRepository.findImageIdsByCatalogName(title);
            // Fetch images for each ID, ensuring we fetch their associated catalogs too
            List<ImageModel> images = imageIds.stream()
                    .map(imageRepository::findByIdWithCatalogs)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(this::convertToModalImage)
                    .collect(Collectors.toList());
            results.add(new CatalogImagesDTO(title, images));
        }
        return results;
    }

    // TODO: Connect this to an endpoint. DOES IT MATTER, if we can filter by rating on the frontend?
    @Transactional(readOnly = true)
    public Optional<ImageModel> getImageByIdAndMinRating(Long imageId, Integer minRating) {
        Optional<ImageEntity> imageOpt = imageRepository.findByIdWithCatalogsAndMinRating(imageId, minRating);
        return imageOpt.map(this::convertToModalImage);
    }


    private ImageModel convertToModalImage(ImageEntity image) {
        List<String> catalogNames = image.getCatalogs().stream().map(CatalogEntity::getName).collect(Collectors.toList());

        ImageModel modalImage = new ImageModel(); //  is not public in 'edens.zac.portfolio.backend.model.ModalImage'. Cannot be accessed from outside package
        modalImage.setTitle(image.getTitle());
        modalImage.setImageWidth(image.getImageWidth());
        modalImage.setImageHeight(image.getImageHeight());
        modalImage.setIso(image.getIso());
        modalImage.setAuthor(image.getAuthor());
        modalImage.setRating(image.getRating());
        modalImage.setFStop(image.getFStop());
        modalImage.setLens(image.getLens());
        modalImage.setCatalog(catalogNames);
        modalImage.setBlackAndWhite(image.getBlackAndWhite());
        modalImage.setShutterSpeed(image.getShutterSpeed());
        modalImage.setRawFileName(image.getRawFileName());
        modalImage.setCamera(image.getCamera());
        modalImage.setFocalLength(image.getFocalLength());
        modalImage.setLocation(image.getLocation());
        modalImage.setImageUrlWeb(image.getImageUrlWeb());
        modalImage.setImageUrlSmall(image.getImageUrlSmall());
        modalImage.setImageUrlRaw(image.getImageUrlRaw());
        modalImage.setCreateDate(image.getCreateDate());
        modalImage.setUpdateDate(image.getUpdateDate());
        modalImage.setId(image.getId());
        return modalImage;
    }

    // TODO: Frontend CDN selects images, calls this endpoint to GET metadata back again, without uploading
    public Map<String, String> getImageMetadata(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata(metadata);
            Map<String, String> imageReturnMetadata = new HashMap<>();
            imageReturnMetadata.put("focalLength", extractValueForKey(directoriesList, "Focal Length 35"));
            imageReturnMetadata.put("fStop", extractValueForKey(directoriesList, "F-Number"));
            imageReturnMetadata.put("shutterSpeed", extractValueForKey(directoriesList, "Shutter Speed Value"));
            imageReturnMetadata.put("iso", String.valueOf(Integer.valueOf(Objects.requireNonNull(extractValueForKey(directoriesList, "ISO Speed Ratings")))));
            imageReturnMetadata.put("author", extractValueForKey(directoriesList, "Artist"));
            imageReturnMetadata.put("lens", extractValueForKey(directoriesList, "Lens Model"));
            imageReturnMetadata.put("lensSpecific", extractValueForKey(directoriesList, "Lens Specification"));
            imageReturnMetadata.put("camera", extractValueForKey(directoriesList, "Model"));
            imageReturnMetadata.put("date", extractValueForKey(directoriesList, "Date/Time Original"));
            imageReturnMetadata.put("imageHeight", extractValueForKey(directoriesList, "Image Height"));
            imageReturnMetadata.put("imageWidth", extractValueForKey(directoriesList, "Image Width"));
            imageReturnMetadata.put("blackAndWhite", String.valueOf(Objects.equals(extractValueForKey(directoriesList, "crs:ConvertToGrayscale"), "True")));
            imageReturnMetadata.put("rawFileName", extractValueForKey(directoriesList, "crs:RawFileName"));
            imageReturnMetadata.put("rating", extractValueForKey(directoriesList, "xmp:Rating"));
            imageReturnMetadata.put("title", file.getOriginalFilename());

            return imageReturnMetadata;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

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

//    @Override
//    public List<PhotoCategoryPackage> getImagesByCategory(List<String> categories) {
//        return null;
//    }

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
                    for (Map.Entry<String, String> entry : xmpProperties.entrySet()) {
                        // Prefix XMP properties to distinguish them from standard tags
                        tagMap.put(entry.getKey(), entry.getValue());
                    }
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

    private String generateS3Key(String filename, ImageType type) {
        // generate a unique key for s3
        // format: yyyy/MM/original_filename
        LocalDate now = LocalDate.now();
        return String.format("%s/%d/%02d/%s",
                type.getFolderName(),
                now.getYear(),
                now.getMonthValue(),
                filename);
    }

    private enum ImageType {
        WEB("web"),
        THUMBNAIL("thumbnail");

        private final String folderName;

        ImageType(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }
}
