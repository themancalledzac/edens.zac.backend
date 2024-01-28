package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.Image;

import java.util.List;
import java.util.UUID;

public interface ImageService {


    Image getImageByUuid(UUID uuid);

    List<Image> getImageByCategory(String category);
}
