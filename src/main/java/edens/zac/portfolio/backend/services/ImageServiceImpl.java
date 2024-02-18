package edens.zac.portfolio.backend.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.Image;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

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

    public Image createImage(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            XmpDirectory xmpDirectory = metadata.getFirstDirectoryOfType(XmpDirectory.class);

            if (xmpDirectory != null) {
                System.out.println("metadata: " + metadata.toString());
                System.out.println("xmpDirectory: " + xmpDirectory);
                System.out.println("xmpDirectory: " + xmpDirectory.getXmpProperties().toString());
                String ratingKey = "xmp:Rating"; // Adjust based on the actual format
                String rating = xmpDirectory.getXmpProperties().get(ratingKey);

                String xmpDirectoryString = printDirectory(xmpDirectory);

                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(xmpDirectory.getXmpProperties());
                System.out.println(json);

                String ratingTest = metadata.getFirstDirectoryOfType(XmpDirectory.class).getXmpProperties().get("xmp:Rating");

//                String rating = xmpDirectory.getXmpProperties().get("xmp: Rating");
//                if (rating != null) {
//                    System.out.println("rating is: " + Integer.parseInt(rating));
////                    return Integer.parseInt(rating);
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle the exception appropriately
        }
        return null; // Rating not found or an error occurred

        // old

        //        try {
//            ImageMetadata metadata = Imaging.getMetadata(file.getInputStream(), file.getOriginalFilename());
//            if (metadata instanceof JpegImageMetadata jpegImageMetadata) {
//                TiffImageMetadata exifMetadata = jpegImageMetadata.getExif();
//                for (ImageMetadataItem item : metadata.getItems()) {
//                    System.out.println(item);
////                    if (item.getKeyword().equalsIgnoreCase("Rating")) {
////                        String ratingValue = item.getText();
////                        return Integer.parseInt(ratingValue);
//                }
//            }
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
    }

    private String printDirectory(XmpDirectory xmpDirectory) {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(xmpDirectory.getXmpProperties());
        System.out.println(json);

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
