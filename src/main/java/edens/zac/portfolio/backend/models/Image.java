package edens.zac.portfolio.backend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * * * IMAGE * *
 * <p>
 * Image is all data we can get our hands on about an image, including what other tables it's in.
 * We should be able to have null for most fields, other than `uuid, imageMedium`
 * <p>
 * <p>
 * <p>
 * * Image File Sizes *
 * <p>
 * AWS image url (internal preferred)
 * <p>
 * Large is only used for full size images, which would be more 'buy'? Maybe 5 stars only have Large in use
 * <p>
 * This could just be for zoomable images for scale
 * <p>
 * For full screen, medium is enough. (Still very optimized)
 * <p>
 * Small is probably maybe half screen, probably more like a quarter of the screen.
 */
@Entity
@Table(name = "image")
public class Image {

    public Image(long uuid, String urlMedium, int rating) {
        this.uuid = uuid;
        this.urlMedium = urlMedium;
        this.rating = rating;
    }

    @Id
    @Column(name = "imageUuid")
    @Getter
    @Setter
    @GeneratedValue(strategy = GenerationType.UUID)
    private long uuid;

    /**
     * * Title *
     * Nullable(? maybe just empty ok)
     * Assumption is 5 stars would prob have a title
     */
    @Getter
    @Setter
    @Column(name = "title")
    private String title;

    @Getter
    @Setter
    @Column(name = "imageUrlLarge")
    private String urlLarge;

    @Getter
    @Setter
    @Column(name = "imageUrlMedium")
    private String urlMedium;

    @Getter
    @Setter
    @Column(name = "imageUrlSmall")
    private String urlSmall;

    @Getter
    @Setter
    @Column(name = "rating")
    private int rating;

    @Getter
    @Setter
    @Column(name = "date")
    private Date date;

    @Getter
    @Setter
    @Column(name = "location")
    private String location;

    @Getter
    @Setter
    @Column(name = "adventure")
    private String adventure;

    public Object getImage() {

    }


    //TODO: Future Goals:
    // Looking to get Image Metadata into this object
    // Would need a function/Script that does the following:
    // - On Upload,
    // - Pull all Metadate from Image, parse through and add to object for upload requests
    // - Have a check for, Already uploaded? basically, provide AWS imageUrl, attach it to 'New Image'
    // - Upload Image to AWS
    // - Add ImageUrl from an AWS successful POST/GET to our Image Object (Fill In, or initially not?
    // - INSERT INTO image WHERE UUID = uuid; (or something like that)
    // - Side note, we need this Automated, so we need it to go through lots of images and process them.
    // - - Get all urls into a List (AWS call available i'm sure)
    // - - Run a 4 loop through all image urls to create a database copy
    // - - This would really be simple if we could ADD/MANIPULATE data on the frontend
    // - - - ALA' open image, click 'edit', metadata pops up with editable fields, save and it does an UPDATE
    // - Verify Successful upload
    // - Figure out how/what to do about multiple versions of the image. I guess we'd just upload those manually?


}

