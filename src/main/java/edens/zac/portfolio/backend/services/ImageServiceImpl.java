package edens.zac.portfolio.backend.services;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;

    public ImageServiceImpl(ImageRepository imageRepository, CatalogRepository catalogRepository) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
    }

    @Override
    @Transactional
    public Map<String, String> postImage(MultipartFile file) {
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

            // SHORT TERM solution for adding catalogs.
            // Will need to get some sort of UI or otherwise to add these further down the road.
            List<String> catalogNames = new ArrayList<>();
            catalogNames.add("Europe");
            catalogNames.add("Vienna");

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
                    .location(null)
                    .imageUrlLarge(null)
                    .imageUrlSmall(null)
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

//            imageRepository.save(builtImage);
            return imageReturnMetadata;
        } catch (DataIntegrityViolationException | IOException |
                 ImageProcessingException e) {
            throw new DataIntegrityViolationException(String.valueOf(e));
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
        modalImage.setImageUrlLarge(image.getImageUrlLarge());
        modalImage.setImageUrlSmall(image.getImageUrlSmall());
        modalImage.setImageUrlRaw(image.getImageUrlRaw());
        modalImage.setCreateDate(image.getCreateDate());
        modalImage.setUpdateDate(image.getUpdateDate());
        modalImage.setId(image.getId());
        return modalImage;
    }

    // DEPRECATED
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
            if (directory instanceof XmpDirectory) {
                XmpDirectory xmpDirectory = (XmpDirectory) directory;
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
}
