package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AdminHomeTileRepository;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminHomeServiceTest {

  @Mock private AdminHomeTileRepository adminHomeTileRepository;
  private AdminHomeService adminHomeService;

  @BeforeEach
  void setUp() {
    adminHomeService = new AdminHomeService(adminHomeTileRepository);
  }

  @Nested
  class GetTiles {

    @Test
    void delegatesToRepositoryAndReturnsResult() {
      List<Records.AdminHomeTileResponse> tiles =
          List.of(
              new Records.AdminHomeTileResponse("home", "https://cdn.example.com/home.jpg", 0),
              new Records.AdminHomeTileResponse("all-collections", null, 1));
      when(adminHomeTileRepository.findAllWithCover()).thenReturn(tiles);

      List<Records.AdminHomeTileResponse> result = adminHomeService.getTiles();

      assertThat(result).isSameAs(tiles);
    }

    @Test
    void emptyRepositoryReturnsEmptyList() {
      when(adminHomeTileRepository.findAllWithCover()).thenReturn(List.of());

      assertThat(adminHomeService.getTiles()).isEmpty();
    }
  }
}
