package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class ImageMetadata {

    private UUID uuid;
    private Integer version;
    private Integer imageWidth;
    private Integer imageHeight;
    private String aperture; // will require translation from exif data: 7983/3509 (2.275) into F-stop, using the formula Av=log₂A², where Av is ApertureValue, and A is f-number;
    // // https://en.wikipedia.org/wiki/APEX_system
    // http://web.mit.edu/graphics/src/Image-ExifTool-6.99/html/faq.html#Q4
    private String foalLength;
    private String cameraMake; // i.e. "Nikon:
    private String cameraModel; // i.e. "Z6"
    private String lensModel; // i.e. "70-200f2.8"
    private Boolean horizontal; // is horizontal true, vertical false
}
