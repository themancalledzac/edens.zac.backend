package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class BlogModel {
    private Long id;
    private String title;
    private LocalDate date;
    private String location;
    private Integer priority; // 1 | 2 | 3 | 4 - 1 being 'best', 4 worst
    private String paragraph; // Break the content into paragraphs at some point
    private List<ImageModel> images;
    private String author;
    private List<String> tags;
    private String coverImageUrl;
    private String slug;
    private LocalDateTime createdDate;
}
