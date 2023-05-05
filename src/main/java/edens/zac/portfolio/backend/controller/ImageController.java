package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.models.Image;
import edens.zac.portfolio.backend.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class ImageController {

    @Autowired
    private ImageRepository imageRepository;

    @GetMapping("/images")
    public List<Image> getImages() {
        return imageRepository.findAll();
    }

    @GetMapping("/image/{uuid}")
    public Image getImage(@RequestParam("imageUuid") Long uuid) {
         return ResponseEntity.of(imageRepository.findById(uuid).map(Image::getImage))
    }

    // https://stackoverflow.com/questions/37253571/spring-data-jpa-difference-between-findby-findallby

}
