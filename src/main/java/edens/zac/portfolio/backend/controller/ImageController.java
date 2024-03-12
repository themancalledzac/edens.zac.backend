package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.Image;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/image")
public class ImageController {

    private final ImageService imageService;

//    /**
//     * @param imageUuid - UUID that connects our image to all it's metadata, including S3 small/large/raw image locations.
//     * @return Image object, as seen in '/model/Image'
//     * <p>
//     * Specific Image request based on UUID. This will likely be used on the site, when looking through a LIST of Images,
//     * and wanting to get more information on that specific image. This endpoint will likely add additional information
//     * not generally in the Image object, such as specific metadata that normally wouldn't be included in a general List<Image> call.
//     * This reasoning only works if we keep our Image table as sort of GENERIC, and that we would have more specific metadata in a separate
//     * table such as ImageMetadata. This table would be connected, probably, by a foreignKey associated with ImageUuid.
//     */
//    @RequestMapping(value = "/{imageUuid}", method = RequestMethod.GET)
//    public Image getImageByUuid(@PathVariable("imageUuid") UUID imageUuid) {
//
//        log.debug("Get Image by Uuid - In Controller");
//
//        return imageService.getImageByUuid(imageUuid);
//    }

//    @RequestMapping(value = "/category/{category}", method = RequestMethod.GET)
//    public List<Image> getImageByCategory(@PathVariable("category") String category) {
//
//        log.debug("Get Images by Category - in Controller");
//
//        return imageService.getImageByCategory(category);
//    }

//    @RequestMapping(value = "/date/{date}", method = RequestMethod.GET)
//    public List<Image> getImageByDate(@PathVariable("date") String date) {
//
//        log.debug("Get Images By Date - in Controller");
//
//        return imageService.getImageByDate(date);
//    }

    //    /**
//     * @return simple Image object from DB, indicating that we have saved our Image.
//     * <p>
//     * This will be one of the more important endpoints we have, and initially something that is Postman only.
//     * The idea of this endpoint is twofold. First, is uploading our Image to a specific AWS S3 bucket, dependent on Date/name/etc.
//     * Second is creating a Database object for that Image, so we can find it again later.
//     * The difficulty with this, is, that I anticipate needing to upload MULTIPLE versions of an image ( ie. JPEG_SMALL/JPEG_LARGE/RAW )
//     * With this fact, We need to figure out logic for, `if(imageTitle.exists() && imageDate.exists()) { inheritUUID()};`
//     * possible documentation: `https://www.bezkoder.com/spring-boot-upload-multiple-files/`
//     * What would be IDEAL, is if we can do BULK upload, in which we are uploading all versions of a file at the same time.
//     * possible documentation for image compression: `https://www.baeldung.com/java-image-compression-lossy-lossless`
//     * <p>
//     * MVP will probably be a single image upload, initially, and perhaps also an 'AddImageToDB' method or endpoint, for images already in S3.
//     */
//    @RequestMapping(value = "/saveImage", method = RequestMethod.POST)
//    public ResponseEntity<Void> saveImage(@RequestBody Image image) {
//        Image savedImage = imageService.saveImage(image);
//        System.out.printf("%s rated Image saved to database.%n", savedImage.getRating());
//
//        return new ResponseEntity<>((HttpStatus.CREATED));
//    }
    @PostMapping("/postImages")
    public List<Map<String, String>> postImages(@RequestParam("images") List<MultipartFile> files) {
        return files.stream().map(imageService::postImage) // file -> imageService.getImageMetadata(file)
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/getImageMetadata", method = RequestMethod.POST)
    public Map<String, String> getImageMetadata(@RequestParam("image") MultipartFile file) {
        return imageService.getImageMetadata(file);
    }

    @PostMapping("/getBatchImageMetadata")
    public List<Map<String, String>> getBatchImageMetadata(@RequestParam("images") List<MultipartFile> files) {
        return files.stream().map(imageService::getImageMetadata) // file -> imageService.getImageMetadata(file)
                .collect(Collectors.toList());
    }

    // https://stackoverflow.com/questions/37253571/spring-data-jpa-difference-between-findby-findallby

    /**
     * @return List of Images ( successfully created )
     * <p>
     * This will be our 'Batch Create' endpoint, for when we are uploading multiple images simultaneously.
     * Will require a LARGE amount of work to be functional, and will have to account for all possible edge cases.
     * Initially more of a POSTMAN endpoint rather than website endpoint.
     * Will need to have some sort of Authentication to not let ANYONE just use our AWS S3 account.
     */
    @RequestMapping(value = "/images", method = RequestMethod.POST)
    public List<Image> batchCreateImages() {
        return null;
    }


//    @RequestMapping(value = "/getImagesByCategory", method = RequestMethod.GET)
//    public List<PhotoCategoryPackage> getImagesByCategory(@RequestParam List<String> categories) {
//        return imageService.getImagesByCategory(categories);
//    }
}
