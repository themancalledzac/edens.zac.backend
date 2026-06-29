package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordsTest {

  @Test
  void adminHomeTileResponse_holdsExpectedFields() {
    var tile =
        new Records.AdminHomeTileResponse(
            "home", "https://cdn.example.com/home.jpg", 1600, 1067, 0);

    assertThat(tile.tileKey()).isEqualTo("home");
    assertThat(tile.coverImageUrl()).isEqualTo("https://cdn.example.com/home.jpg");
    assertThat(tile.coverImageWidth()).isEqualTo(1600);
    assertThat(tile.coverImageHeight()).isEqualTo(1067);
    assertThat(tile.displayOrder()).isEqualTo(0);
  }

  @Test
  void adminHomeTileResponse_nullCoverImageUrl_isAllowed() {
    var tile = new Records.AdminHomeTileResponse("all-collections", null, null, null, 1);

    assertThat(tile.coverImageUrl()).isNull();
    assertThat(tile.coverImageWidth()).isNull();
    assertThat(tile.coverImageHeight()).isNull();
    assertThat(tile.displayOrder()).isEqualTo(1);
  }
}
