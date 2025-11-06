//package edens.zac.portfolio.backend.entity;
//
//import jakarta.persistence.Entity;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//import jakarta.persistence.Table;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//
//import java.time.LocalDateTime;
//
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//@Entity
//@Table(name = "content_collection_home_card")
//public class ContentCollectionHomeCardEntity {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    // Core information
//    private String title;
//    private String cardType; // blog | catalog | text | collection
//    private String location;
//    private String date;
//
//    // Display/Sort properties
//    private Integer priority;
//    private String coverImageUrl;
//    private String text;
//    private boolean isActiveHomeCard;
//
//    // Navigation
//    private String slug;
//
//    // Optional Reference (can be null for collection/text types)
//    private Long referenceId;
//
//    // Tracking
//    private LocalDateTime createdDate;
//}
