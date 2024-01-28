package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.Image;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
