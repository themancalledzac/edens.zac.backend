package edens.zac.portfolio.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
