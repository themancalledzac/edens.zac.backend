package edens.zac.portfolio.backend.model;

import jdk.jshell.Snippet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class HomeCardModel {
    private Long id;
    private String title;
    private String cardType;
    private String location;
    private String date;
    private Integer priority;
    private String coverImageUrl;
    private String slug;
    private String text;
}
