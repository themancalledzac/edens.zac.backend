package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/image")
public class ImageController {

    private final ImageService imageService;

    /**
     * @param imageId - ID that connects our image to all it's metadata, including S3 small/large/raw image locations.
     * @return Image object, as seen in '/model/Image'
     * <p>
     * Specific Image request based on UUID. This will likely be used on the site, when looking through a LIST of Images,
     * and wanting to get more information on that specific image. This endpoint will likely add additional information
     * not generally in the Image object, such as specific metadata that normally wouldn't be included in a general List<Image> call.
     * This reasoning only works if we keep our Image table as sort of GENERIC, and that we would have more specific metadata in a separate
     * table such as ImageMetadata. This table would be connected, probably, by a foreignKey associated with ImageUuid.
     */
    @RequestMapping(value = "/getById/{imageId}", method = RequestMethod.GET)
    public ImageModel getImageById(@PathVariable("imageId") Long imageId) {
        log.debug("Get Image by Uuid - In Controller");
        return imageService.getImageById(imageId);
    }

    @GetMapping("/getImagesByCatalogs/{catalog}")
    public List<ImageModel> getImagesByCatalog(@PathVariable("catalog") String catalog) {
        return imageService.getAllImagesByCatalog(catalog);
    }

    /**
     * TODO: Update this endpoint to take in a list of images AS WELL as a list of associated image metadata
     * TODO: Need to figure out our upload strategy. Upload IMAGES & List<objects>, where each object is associated to each image?
     * MAIN endpoint for posting images to database
     * // <a href="http://localhost:8080/api/v1/image/getImagesByCatalogs?catalogs=Amsterdam,Paris">...</a>
     *
     * @param files - Add a List of files (POSTMAN: Body< form-data< Key:Images(File), Value(${your-images}) )
     * @return - A Json List of the metadata added to the database
     * @CrossOrigin(origins = "http://localhost:3000") // Allow only from your Local React app
     * @GetMapping("/getImagesByCatalogs") public ResponseEntity<?> getImagesByMultipleCatalogs(@RequestParam("catalogs") String catalogss) {
     * List<String> catalogsNames = Arrays.asList(catalogs.split(","));
     * List<CatalogsImagesDTO> results = imageService.getAllImagesByCatalogs(catalogsNames);
     * return ResponseEntity.ok(results);
     * }
     */
    @PostMapping("/postImages")
    public List<Map<String, String>> postImages(@RequestParam("images") List<MultipartFile> files) {
        return files.stream().map(imageService::postImages) // file -> imageService.getImageMetadata(file)
                .collect(Collectors.toList());
    }

    /**
     * Endpoint to Get Image Metadata for 'n' number of Images
     * <p>
     *
     * @param {List<MultipartFile>>} files
     * @return {List<object></object>} List of our image metadata objects being returned
     */
    @PostMapping("/getBatchImageMetadata")
    public List<Map<String, String>> getBatchImageMetadata(@RequestPart("images") List<MultipartFile> files) {
        System.out.println("zac was here");
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
    public List<ImageModel> batchCreateImages() {
        return null;
    }
    // TODO: Update thie return to include categories, as would a regular 'get image' wou

//    @RequestMapping(value = "/getImagesByCategory", method = RequestMethod.GET)
//    public List<PhotoCategoryPackage> getImagesByCategory(@RequestParam List<String> categories) {
//        return imageService.getImagesByCategory(categories);
//    }
}
