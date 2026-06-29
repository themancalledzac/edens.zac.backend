package edens.zac.portfolio.backend.controller.dev;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.AdminHomeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminHomeControllerDevTest {

  private MockMvc mockMvc;

  @Mock private AdminHomeService adminHomeService;
  @InjectMocks private AdminController controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getTiles_returnsTileListWith200() throws Exception {
    List<Records.AdminHomeTileResponse> tiles =
        List.of(
            new Records.AdminHomeTileResponse(
                "home", "https://cdn.example.com/home.jpg", 1200, 800, 0),
            new Records.AdminHomeTileResponse("all-collections", null, null, null, 1));
    when(adminHomeService.getTiles()).thenReturn(tiles);

    mockMvc
        .perform(get("/api/admin/admin-home/tiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].tileKey", is("home")))
        .andExpect(jsonPath("$[0].coverImageUrl", is("https://cdn.example.com/home.jpg")))
        .andExpect(jsonPath("$[0].coverImageWidth", is(1200)))
        .andExpect(jsonPath("$[0].coverImageHeight", is(800)))
        .andExpect(jsonPath("$[0].displayOrder", is(0)))
        .andExpect(jsonPath("$[1].tileKey", is("all-collections")))
        .andExpect(jsonPath("$[1].coverImageUrl", nullValue()))
        .andExpect(jsonPath("$[1].coverImageWidth", nullValue()))
        .andExpect(jsonPath("$[1].coverImageHeight", nullValue()));
    verify(adminHomeService).getTiles();
  }

  @Test
  void getTiles_emptyList_returns200WithEmptyArray() throws Exception {
    when(adminHomeService.getTiles()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/admin/admin-home/tiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    verify(adminHomeService).getTiles();
  }
}
