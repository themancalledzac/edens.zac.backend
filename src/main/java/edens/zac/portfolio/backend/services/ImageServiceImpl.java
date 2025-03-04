package edens.zac.portfolio.backend.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogImagesDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import edens.zac.portfolio.backend.repository.ImageRepository;
import edens.zac.portfolio.backend.specification.ImageSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final ImageProcessingUtil imageProcessingUtil;

    @Autowired
    public ImageServiceImpl(
            ImageRepository imageRepository,
            ImageProcessingUtil imageProcessingUtil) {
        this.imageRepository = imageRepository;
        this.imageProcessingUtil = imageProcessingUtil;

        log.info("ImageServiceImpl initialized");
    }

    @Override
    @Transactional
    public Map<String, String> postImages(MultipartFile file, String type) {
        try {
            log.info("Posting Image: {} of type: {}", file.getOriginalFilename(), type);

            // Part 1: Extract metadata
            // Return it even if we hit any exceptions later on
            Map<String, String> imageMetadata = imageProcessingUtil.extractImageMetadata(file);

            // Part 2: Determine catalog name
            String contextName = "default";

            // Part 3: add Catalog name to image if exists
            // TODO: For now, this lives here, but we should assume that 'uploadImage' will be done via 'createCatalog' or 'createBlog'.
            if ("catalog".equals(type) && imageMetadata.containsKey("data")) {
                String date = imageMetadata.get("date");
                if (date != null && date.length() >= 7) {
                    contextName = date.substring(0, 7).replace(':', '-');
                }
            }

            // Part 3: Process and Save the image using the shared utility
            // TODO: Update our 'contextName' so that it is from our API call, not from a 'date' value
            ImageEntity savedImage = imageProcessingUtil.processAndSaveImage(file, type, contextName);
            log.info("Image processed and saved with ID: {}", savedImage.getId());

            // Add ID to metadata response
            imageMetadata.put("id", savedImage.getId().toString());
            imageMetadata.put("imageUrlWeb", savedImage.getImageUrlWeb());

            return imageMetadata;
        } catch (Exception e) {
            log.error("Error in postImages: {}", e.getMessage(), e);

            // Even on error, try to return at least the basic metadata
            Map<String, String> errorMetadata = new HashMap<>();
            errorMetadata.put("title", file.getOriginalFilename());
            errorMetadata.put("error", e.getMessage());
            return errorMetadata;
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
//        Set<Long> imageHeaders = imageRepository.findImageIdsByCatalogNameAndCriteria(catalogTitle);

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
    public List<ImageModel> updateImages(ImageModel imageModel) {
        return null;
    }

    @Override
    public List<ImageModel> searchByData(ImageSearchModel searchParams) {

        List<ImageEntity> results = imageRepository.findAll(
                ImageSpecification.buildImageSpecification(searchParams)
        );

        return results.stream()
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

    // TODO: Delete once not in use
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

    // TODO: Delete once not in use
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
}
