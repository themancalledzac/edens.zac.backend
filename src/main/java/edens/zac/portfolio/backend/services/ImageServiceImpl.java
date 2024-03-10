package edens.zac.portfolio.backend.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.Image;
import edens.zac.portfolio.backend.model.PhotoCategoryPackage;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    @Autowired
    private ImageRepository imageRepository;

    private Map<UUID, Image> imageMap;

    public ImageServiceImpl() {
        this.imageMap = new HashMap<>();

        Image Image01 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0001.jpg")
                .location("seattle")
                .imageUrlLarge("/./large/Image01/0001.jpg/aws.com")
                .imageUrlSmall("/./small/Image01/0001.jpg/aws.com")
                .imageUrlRaw("/./raw/Image01/0001.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_12th")
                .build();

        Image Image02 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0002.jpg")
                .location("seattle")
                .imageUrlLarge("/./large/Image02/0002.jpg/aws.com")
                .imageUrlSmall("/./small/Image02/0002.jpg/aws.com")
                .imageUrlRaw("/./raw/Image02/0002.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_12th")
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();

        Image Image03 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0003.jpg")
                .location("seattle")
                .imageUrlLarge("/./large/Image03/0001.jpg/aws.com")
                .imageUrlSmall("/./small/Image03/0001.jpg/aws.com")
                .imageUrlRaw("/./raw/Image03/0003.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_12th")
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();

        Image Image04 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0004.jpg")
                .location("bellevue")
                .imageUrlLarge("/./large/Image01/0001.jpg/aws.com")
                .imageUrlSmall("/./small/Image01/0001.jpg/aws.com")
                .imageUrlRaw("/./raw/Image01/0001.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_12th")
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();

        Image Image05 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0005.jpg")
                .location("seattle")
                .imageUrlLarge("/./large/Image01/0001.jpg/aws.com")
                .imageUrlSmall("/./small/Image01/0001.jpg/aws.com")
                .imageUrlRaw("/./raw/Image01/0001.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_12th")
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();

        Image Image06 = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name("0005.jpg")
                .location("toronto")
                .imageUrlLarge("/./large/Image01/0001.jpg/aws.com")
                .imageUrlSmall("/./small/Image01/0001.jpg/aws.com")
                .imageUrlRaw("/./raw/Image01/0001.jpg/aws.com")
                .rating(5)
                .date("2023")
                .adventure("city_walk_december_10th")
                .build();

        imageMap.put(Image01.getUuid(), Image01);
        imageMap.put(Image02.getUuid(), Image02);
        imageMap.put(Image03.getUuid(), Image03);
        imageMap.put(Image04.getUuid(), Image04);
        imageMap.put(Image05.getUuid(), Image05);
        imageMap.put(Image06.getUuid(), Image06);
    }

    @Override
    public Image getImageByUuid(UUID uuid) {
        return imageMap.get(uuid);
    }

    @Override
    public List<Image> getImageByCategory(String category) {

        return imageMap.values()
                .stream()
                .filter(image -> category.equals(image.getAdventure()))
                .collect(Collectors.toList());
    }

    @Override
    public Image saveImage(Image image) {

        Image savedImage = Image.builder()
                .uuid(UUID.randomUUID())
                .version(1)
                .name(image.getName())
                .location(image.getLocation())
                .imageUrlSmall(image.getImageUrlSmall())
                .imageUrlLarge(image.getImageUrlLarge())
                .imageUrlRaw(image.getImageUrlRaw())
                .rating(image.getRating())
                .date(image.getDate())
                .adventure(image.getAdventure())
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();
        imageMap.put(savedImage.getUuid(), savedImage);
        return savedImage;
    }

    public List<Image> getImageByDate(String date) {

        return imageMap.values().stream()
                .filter(image -> date.equals(image.getDate()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> postImage(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata(metadata);
            Map<String, String> imageReturnMetadata = new HashMap<>();
            imageReturnMetadata.put("focalLength", extractValueForKey(directoriesList, "Focal Length 35"));
            imageReturnMetadata.put("fStop", extractValueForKey(directoriesList, "F-Number"));
            imageReturnMetadata.put("shutterSpeed", extractValueForKey(directoriesList, "Shutter Speed Value"));
            imageReturnMetadata.put("iso", extractValueForKey(directoriesList, "ISO Speed Ratings"));
            imageReturnMetadata.put("author", extractValueForKey(directoriesList, "Artist"));
            imageReturnMetadata.put("lens", extractValueForKey(directoriesList, "Lens Model"));
            imageReturnMetadata.put("lensSpecific", extractValueForKey(directoriesList, "Lens Specification"));
            imageReturnMetadata.put("camera", extractValueForKey(directoriesList, "Model"));
            imageReturnMetadata.put("date", extractValueForKey(directoriesList, "Date/Time Original"));
            imageReturnMetadata.put("imageHeight", extractValueForKey(directoriesList, "Image Height"));
            imageReturnMetadata.put("imageWidth", extractValueForKey(directoriesList, "Image Width"));
            imageReturnMetadata.put("horizontal", String.valueOf(isHorizontal(extractValueForKey(directoriesList, "Image Height"), extractValueForKey(directoriesList, "Image Width"))));
            imageReturnMetadata.put("blackAndWhite", String.valueOf(Objects.equals(extractValueForKey(directoriesList, "crs:ConvertToGrayscale"), "True")));
            imageReturnMetadata.put("rawFileName", extractValueForKey(directoriesList, "crs:RawFileName"));
            imageReturnMetadata.put("rating", extractValueForKey(directoriesList, "xmp:Rating"));
            imageReturnMetadata.put("title", file.getOriginalFilename());

            Image builtImage = Image.builder()
                    .uuid(UUID.randomUUID())
                    .version(1)
                    .name(file.getName())
                    .location(null)
                    .imageUrlSmall(null)
                    .imageUrlLarge(null)
                    .imageUrlRaw(null)
                    .rating(Integer.valueOf(extractValueForKey(directoriesList, "xmp:Rating")))
                    .date(extractValueForKey(directoriesList, "Date/Time Original"))
                    .adventure(null)
//                    .createDate(LocalDateTime.parse(extractValueForKey(directoriesList, "Date/Time Original"))) // TODO: Looks like this line doesn't work
                    .updateDate(LocalDateTime.now())
                    .build();
            imageMap.put(builtImage.getUuid(), builtImage);
            imageRepository.save(builtImage);
            return imageReturnMetadata;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, String> getImageMetadata(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            List<Map<String, Object>> directoriesList = collectAllDirectoriesMetadata(metadata);
            Map<String, String> imageReturnMetadata = new HashMap<>();
            imageReturnMetadata.put("focalLength", extractValueForKey(directoriesList, "Focal Length 35"));
            imageReturnMetadata.put("fStop", extractValueForKey(directoriesList, "F-Number"));
            imageReturnMetadata.put("shutterSpeed", extractValueForKey(directoriesList, "Shutter Speed Value"));
            imageReturnMetadata.put("iso", extractValueForKey(directoriesList, "ISO Speed Ratings"));
            imageReturnMetadata.put("author", extractValueForKey(directoriesList, "Artist"));
            imageReturnMetadata.put("lens", extractValueForKey(directoriesList, "Lens Model"));
            imageReturnMetadata.put("lensSpecific", extractValueForKey(directoriesList, "Lens Specification"));
            imageReturnMetadata.put("camera", extractValueForKey(directoriesList, "Model"));
            imageReturnMetadata.put("date", extractValueForKey(directoriesList, "Date/Time Original"));
            imageReturnMetadata.put("imageHeight", extractValueForKey(directoriesList, "Image Height"));
            imageReturnMetadata.put("imageWidth", extractValueForKey(directoriesList, "Image Width"));
            imageReturnMetadata.put("horizontal", String.valueOf(isHorizontal(extractValueForKey(directoriesList, "Image Height"), extractValueForKey(directoriesList, "Image Width"))));
            imageReturnMetadata.put("blackAndWhite", String.valueOf(Objects.equals(extractValueForKey(directoriesList, "crs:ConvertToGrayscale"), "True")));
            imageReturnMetadata.put("rawFileName", extractValueForKey(directoriesList, "crs:RawFileName"));
            imageReturnMetadata.put("rating", extractValueForKey(directoriesList, "xmp:Rating"));
            imageReturnMetadata.put("title", file.getOriginalFilename());

            // TODO: Update this so we have imageWidth/imageHeight, and imageRatioWidth/imageRatioHeight as well.

            Image builtImage = Image.builder()
                    .uuid(UUID.randomUUID())
                    .version(1)
                    .name(file.getName())
                    .location(null)
                    .imageUrlSmall(null)
                    .imageUrlLarge(null)
                    .imageUrlRaw(null)
                    .rating(Integer.valueOf(extractValueForKey(directoriesList, "xmp:Rating")))
                    .date(extractValueForKey(directoriesList, "Date/Time Original"))
                    .adventure(null)
//                    .createDate(LocalDateTime.parse(extractValueForKey(directoriesList, "Date/Time Original"))) // TODO: Looks like this line doesn't work
                    .updateDate(LocalDateTime.now())
                    .build();
            imageMap.put(builtImage.getUuid(), builtImage);

            // option 1: PRINT JSON to console
//            String jsonReturn = printDirectoriesMetadataAsJson(directoriesList);
//            saveDirectoriesMetadataAsJsonFile(directoriesList, "testJson/zacTest.json");
//            return printDirectoriesMetadataAsJson(imageReturnMetadata);
            return imageReturnMetadata;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isHorizontal(String height, String width) {
        // Regular expression to remove non-digit characters
        String numericHeight = height.replaceAll("[^\\d]", "");
        String numericWidth = width.replaceAll("[^\\d]", "");
        return Integer.parseInt(numericHeight) <= Integer.parseInt(numericWidth);
    }

    @Override
    public List<PhotoCategoryPackage> getImagesByCategory(List<String> categories) {
        return null;
    }

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

    public String printDirectoriesMetadataAsJson(Map<String, String> directoriesList) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(directoriesList);
        System.out.println(json);
        return json;
    }

    public void saveDirectoriesMetadataAsJsonFile(List<Map<String, Object>> directoriesList, String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), directoriesList);
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

    private String printDirectory(Directory directory) throws JsonProcessingException {
        Map<String, String> tagMap = new HashMap<>();
        for (Tag tag : directory.getTags()) {
            tagMap.put(tag.getTagName(), tag.getDescription());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(tagMap);
        System.out.println(json);
        return json;
    }

    private Map<String, Object> extractMetadata(TiffImageMetadata exifMetadata) throws ImageReadException, IOException {
        Map<String, Object> metadataMap = new HashMap<>();
        // Extract EXIF data and populate the map
        // Example: Extracting GPS info
        TiffImageMetadata.GPSInfo gpsInfo = exifMetadata.getGPS();
        if (gpsInfo != null) {
            double longitude = gpsInfo.getLongitudeAsDegreesEast();
            double latitude = gpsInfo.getLatitudeAsDegreesNorth();
            metadataMap.put("longitude", longitude);
            metadataMap.put("latitude", latitude);
        }
        // Add more metadata extraction here as needed
        return metadataMap;
    }

    private Image convertMapToImage(Map<String, Object> metadataMap) {
        // Convert the metadata map to your custom Image object, setting fields as necessary
        // Ensure this method returns an instance of your custom Image class
        return Image.builder()
                // Assume these fields exist in your Image class; adjust as necessary
                .location(metadataMap.getOrDefault("location", "").toString())
                // Populate other fields from the map
                .build();
    }
}
