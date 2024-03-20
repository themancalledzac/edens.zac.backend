package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.AdventureImagesDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
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

    @GetMapping("/getImagesByAdventure/{adventure}")
    public List<ImageModel> getImagesByAdventure(@PathVariable("adventure") String adventure) {

        return imageService.getAllImagesByAdventure(adventure);
    }

    // http://localhost:8080/api/v1/image/getImagesByAdventures?adventures=Amsterdam,Paris
    @CrossOrigin(origins = "http://localhost:3000") // Allow only from your React app
    @GetMapping("/getImagesByAdventures")
    public ResponseEntity<?> getImagesByMultipleAdventures(@RequestParam("adventures") String adventures) {
        List<String> adventureNames = Arrays.asList(adventures.split(","));
        List<AdventureImagesDTO> results = imageService.getAllImagesByAdventures(adventureNames);
        return ResponseEntity.ok(results);
    }

    /**
     * MAIN endpoint for posting images to database
     *
     * @param files - Add a List of files (POSTMAN: Body< form-data< Key:Images(File), Value(${your-images}) )
     * @return - A Json List of the metadata added to the database
     */
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
    public List<ImageModel> batchCreateImages() {
        return null;
    }


//    @RequestMapping(value = "/getImagesByCategory", method = RequestMethod.GET)
//    public List<PhotoCategoryPackage> getImagesByCategory(@RequestParam List<String> categories) {
//        return imageService.getImagesByCategory(categories);
//    }
}
