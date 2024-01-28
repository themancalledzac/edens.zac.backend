package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.Image;
import edens.zac.portfolio.backend.repository.ImageRepository;
import edens.zac.portfolio.backend.services.ImageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    @Autowired
    private ImageRepository imageRepository;

    @RequestMapping(value = "/{imageUuid}", method = RequestMethod.GET)
    public ResponseEntity<Object> getImage(@PathVariable("imageUuid") UUID imageUuid) {
        return ResponseEntity.of(imageRepository.findById(imageUuid).map(Image::getImage));
    }

    @RequestMapping(value = "/category/{category}", method = RequestMethod.GET)
    public List<Image> getImageByCategory(@PathVariable("category") String category) {

        log.debug("Get Image by Category - in Controller");

        return imageService.getImageByCategory(category);
    }

    // https://stackoverflow.com/questions/37253571/spring-data-jpa-difference-between-findby-findallby


}
