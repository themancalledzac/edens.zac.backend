package edens.zac.portfolio.backend.controller.write;

import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
@Configuration
@Profile("dev")
public class ImageControllerWrite {

    private final ImageService imageService;

    // TODO: Update return value to be a custom 'postImageReturnObject' of some sort, instead of this `<List<Map<String, String>>`

    /**
     * MAIN endpoint for posting images to database
     * // <a href="http://localhost:8080/api/v1/image/getImagesByCatalogs?catalogs=Amsterdam,Paris">...</a>
     *
     * @param files - Add a List of files (POSTMAN: Body< form-data< Key:Images(File), Value(${your-images}) )
     * @param type  - Type is 'blog' or 'catalog'. Determines if we create a blog with associated images, or create a catalog(if not exists) with associated images.
     * @return - A Json List of the metadata added to the database
     * @CrossOrigin(origins = "http://localhost:3000") // Allow only from your Local React app
     * @GetMapping("/getImagesByCatalogs") public ResponseEntity<?> getImagesByMultipleCatalogs(@RequestParam("catalogs") String catalogss) {
     * List<String> catalogsNames = Arrays.asList(catalogs.split(","));
     * List<CatalogsImagesDTO> results = imageService.getAllImagesByCatalogs(catalogsNames);
     * return ResponseEntity.ok(results);
     * }
     */
    @PostMapping("/postImages/{type}")
    public List<Map<String, String>> postImages(@RequestParam("images") List<MultipartFile> files, @PathVariable("type") String type) {
        return files.stream()
                .map(file -> imageService.postImages(file, type))
                .collect(Collectors.toList());
    }

    @PostMapping("/postImagesForCatalog/{catalogTitle}")
    public List<List<ImageModel>> postImagesForCatalog(
            @PathVariable("catalogTitle") String catalogTitle,
            @RequestParam("images") List<MultipartFile> files) {
        return files.stream()
                .map(file -> imageService.postImagesForCatalog(file, catalogTitle))
                .collect(Collectors.toList());
    }

    /**
     * Endpoint to Get Image Metadata for 'n' number of Images
     * <p>
     *
     * @param files - {List<MultipartFile>>}
     * @return {List<object></object>} List of our image metadata objects being returned
     */
    @PostMapping("/getBatchImageMetadata")
    public List<Map<String, String>> getBatchImageMetadata(@RequestPart("images") List<MultipartFile> files) {
        return files.stream().map(imageService::getImageMetadata) // file -> imageService.getImageMetadata(file)
                .collect(Collectors.toList());
    }

    // TODO:: NEW ENDPOINTS!
    //  1. updateImages - Adds Tags, Catalogs(and their state), can Edit: Title, Author(?maybenot?), Location(initially null, based on catalog location maybe? )
    @PutMapping(value = "/update/images")
    public List<List<ImageModel>> updateImages(@RequestBody List<ImageModel> images) {
        System.out.println("UpdateImages updates 'specific' images");
        return images.stream().map(imageService::updateImages)
                .collect(Collectors.toList());
    }

    // TODO:
// TODO:
//  3. UpdateImage ( singular? ) - do we NEED to do that?
    @PutMapping(value = "/update/image")
    public ImageModel updateImage(@RequestBody ImageModel image) {
        System.out.println("UpdateImage updates a specific image.");
        // return imageService.updateImage(image);
        return null;
    }
}
