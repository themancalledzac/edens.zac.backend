package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Data
public class Image {

    private UUID uuid;
    private Integer version;
    private String name;
    private String location;
    private String imageUrlLarge;
    private String imageUrlSmall;
    private String imageUrlRaw; // - Not first priority
    private Integer rating; // - This implies a 1-5 rating scheme, ala Lightroom.
    private String date;
    private String adventure;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
}
