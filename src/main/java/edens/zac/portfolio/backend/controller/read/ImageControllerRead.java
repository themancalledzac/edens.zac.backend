package edens.zac.portfolio.backend.controller.read;

import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/image")
public class ImageControllerRead {

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

    // https://stackoverflow.com/questions/37253571/spring-data-jpa-difference-between-findby-findallby

    /// / TODO: search images by specific metadata, this will take an Image object ( nullable fields ), and search based on those parameters
    /// /  5. getImageByData -
    @GetMapping(value = "/searchByData")
    public List<ImageModel> searchByData(@RequestBody ImageSearchModel searchParams) throws IllegalAccessException {
        System.out.println("Search images by Metadata");
        if (searchParams == null) { // todo: this should be moved to ImageController
            throw new IllegalAccessException("Search parameters cannot be null");
        }
        return imageService.searchByData(searchParams);
    }
}


// TODO: Add a 'GetOrphanedImages' endpoint