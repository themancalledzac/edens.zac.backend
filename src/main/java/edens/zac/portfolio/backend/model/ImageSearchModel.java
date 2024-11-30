package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class ImageSearchModel {

    private Integer rating;
    private Integer isoMin;
    private Integer isoMax;
    private String fStopMin;
    private String fStopMax;
    private String shutterSpeedMin;
    private String shutterSpeedMax;
    private String focalLengthMin;
    private String focalLengthMax;
    private String camera;
    private String lens;
    private Boolean blackAndWhite;
    private String location;
    private String catalog; // TODO: someday might want this to be a List<catalog>??
    private String startDate;
    private String endDate;
    private List<String> tags;
}
