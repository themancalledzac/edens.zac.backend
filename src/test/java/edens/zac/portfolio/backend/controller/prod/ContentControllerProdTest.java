package edens.zac.portfolio.backend.controller.prod;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ImageSearchRequest;
import edens.zac.portfolio.backend.model.ImageSearchResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.MetadataService;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContentControllerProdTest {

  private MockMvc mockMvc;

  @Mock private ContentService contentService;

  @Mock private MetadataService metadataService;

  @InjectMocks private ContentControllerProd contentControllerProd;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(contentControllerProd)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /content/lenses should return all lenses ordered alphabetically")
  void getAllLenses_shouldReturnAllLenses() throws Exception {
    // Arrange
    List<Records.Lens> lenses =
        List.of(
            new Records.Lens(1L, "Canon EF 50mm f/1.4"),
            new Records.Lens(2L, "Nikon AF-S 85mm f/1.8"),
            new Records.Lens(3L, "Sony FE 24-70mm f/2.8"));

    when(metadataService.getAllLenses()).thenReturn(lenses);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/lenses").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id", is(1)))
        .andExpect(jsonPath("$[0].name", is("Canon EF 50mm f/1.4")))
        .andExpect(jsonPath("$[2].name", is("Sony FE 24-70mm f/2.8")));
  }

  @Test
  @DisplayName("GET /content/lenses should return empty list when no lenses exist")
  void getAllLenses_noLenses_shouldReturnEmptyList() throws Exception {
    // Arrange
    when(metadataService.getAllLenses()).thenReturn(List.of());

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/lenses").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("GET /content/locations should return locations with counts")
  void getLocationsWithCounts_shouldReturnLocationsWithCounts() throws Exception {
    // Arrange
    List<Records.LocationWithCounts> locations =
        List.of(
            new Records.LocationWithCounts(1L, "Seattle", "seattle", 3, 15),
            new Records.LocationWithCounts(2L, "Portland", "portland", 1, 8));

    when(metadataService.getLocationsWithCounts()).thenReturn(locations);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/locations").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id", is(1)))
        .andExpect(jsonPath("$[0].name", is("Seattle")))
        .andExpect(jsonPath("$[0].collectionCount", is(3)))
        .andExpect(jsonPath("$[0].imageCount", is(15)))
        .andExpect(jsonPath("$[1].name", is("Portland")));
  }

  @Test
  @DisplayName("GET /content/locations should return empty list when no locations exist")
  void getLocationsWithCounts_noLocations_shouldReturnEmptyList() throws Exception {
    // Arrange
    when(metadataService.getLocationsWithCounts()).thenReturn(List.of());

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/locations").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("GET /content/tags should return all tags")
  void getAllTags_shouldReturnAllTags() throws Exception {
    // Arrange
    List<Records.Tag> tags =
        List.of(
            new Records.Tag(1L, "landscape", "landscape"),
            new Records.Tag(2L, "portrait", "portrait"));

    when(metadataService.getAllTags()).thenReturn(tags);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/tags").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].name", is("landscape")));
  }

  @Test
  @DisplayName("GET /content/cameras should return all cameras")
  void getAllCameras_shouldReturnAllCameras() throws Exception {
    // Arrange
    List<Records.Camera> cameras =
        List.of(new Records.Camera(1L, "Canon EOS R5"), new Records.Camera(2L, "Sony A7 IV"));

    when(metadataService.getAllCameras()).thenReturn(cameras);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/cameras").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].name", is("Canon EOS R5")));
  }

  @Test
  @DisplayName("GET /content/people should return all people")
  void getAllPeople_shouldReturnAllPeople() throws Exception {
    // Arrange
    List<Records.Person> people =
        List.of(
            new Records.Person(1L, "Alice", "alice"),
            new Records.Person(2L, "Bob", "bob"),
            new Records.Person(3L, "Charlie", "charlie"));

    when(metadataService.getAllPeople()).thenReturn(people);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/people").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id", is(1)))
        .andExpect(jsonPath("$[0].name", is("Alice")))
        .andExpect(jsonPath("$[2].name", is("Charlie")));
  }

  @Test
  @DisplayName("GET /content/people should return empty list when no people exist")
  void getAllPeople_noResults_shouldReturnEmptyList() throws Exception {
    // Arrange
    when(metadataService.getAllPeople()).thenReturn(List.of());

    // Act & Assert
    mockMvc
        .perform(get("/api/read/content/people").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Nested
  @DisplayName("GET /content/images/search")
  class SearchImages {

    private ContentModels.Image createTestImage(Long id, String title) {
      return new ContentModels.Image(
          id,
          null,
          title,
          null,
          "https://example.com/img.jpg",
          null,
          0,
          true,
          null,
          null,
          1920,
          1080,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    @Test
    @DisplayName("Should return all images when no filters are provided")
    void searchImages_noFilters_shouldReturnAllImages() throws Exception {
      // Arrange
      List<ContentModels.Image> images =
          List.of(createTestImage(1L, "Image One"), createTestImage(2L, "Image Two"));
      ImageSearchResponse response = new ImageSearchResponse(images, 2, 1);

      when(contentService.searchImages(any(ImageSearchRequest.class))).thenReturn(response);

      // Act & Assert
      mockMvc
          .perform(get("/api/read/content/images/search").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(2)))
          .andExpect(jsonPath("$.content[0].title", is("Image One")))
          .andExpect(jsonPath("$.content[1].title", is("Image Two")))
          .andExpect(jsonPath("$.totalElements", is(2)))
          .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @ParameterizedTest(name = "search with {0}")
    @MethodSource("searchFilterProvider")
    void searchImages_withFilter_shouldReturnResults(
        String name, String paramName, String... paramValues) throws Exception {
      // Arrange
      List<ContentModels.Image> images = List.of(createTestImage(1L, "Filtered Image"));
      ImageSearchResponse response = new ImageSearchResponse(images, 1, 1);

      when(contentService.searchImages(any(ImageSearchRequest.class))).thenReturn(response);

      // Act & Assert
      mockMvc
          .perform(
              get("/api/read/content/images/search")
                  .param(paramName, paramValues)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(1)))
          .andExpect(jsonPath("$.content[0].title", is("Filtered Image")))
          .andExpect(jsonPath("$.totalElements", is(1)));
    }

    static Stream<Arguments> searchFilterProvider() {
      return Stream.of(
          Arguments.of("tagIds", "tagIds", new String[] {"1", "2"}),
          Arguments.of("cameraId", "cameraId", new String[] {"1"}),
          Arguments.of("lensId", "lensId", new String[] {"5"}),
          Arguments.of("locationId", "locationId", new String[] {"3"}),
          Arguments.of("personIds", "personIds", new String[] {"1", "2"}),
          Arguments.of("isFilm", "isFilm", new String[] {"true"}),
          Arguments.of("blackAndWhite", "blackAndWhite", new String[] {"true"}));
    }

    @Test
    @DisplayName("Should return filtered images when date range is provided")
    void searchImages_withDateRange_shouldReturnFilteredImages() throws Exception {
      // Arrange
      List<ContentModels.Image> images = List.of(createTestImage(1L, "Dated Image"));
      ImageSearchResponse response = new ImageSearchResponse(images, 1, 1);

      when(contentService.searchImages(any(ImageSearchRequest.class))).thenReturn(response);

      // Act & Assert
      mockMvc
          .perform(
              get("/api/read/content/images/search")
                  .param("captureStartDate", "2025-01-01")
                  .param("captureEndDate", "2025-12-31")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(1)))
          .andExpect(jsonPath("$.content[0].title", is("Dated Image")))
          .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("Should return filtered images when multiple filters are provided")
    void searchImages_withMultipleFilters_shouldReturnFilteredImages() throws Exception {
      // Arrange
      List<ContentModels.Image> images = List.of(createTestImage(1L, "Multi-filter Image"));
      ImageSearchResponse response = new ImageSearchResponse(images, 1, 1);

      when(contentService.searchImages(any(ImageSearchRequest.class))).thenReturn(response);

      // Act & Assert
      mockMvc
          .perform(
              get("/api/read/content/images/search")
                  .param("tagIds", "1", "2")
                  .param("minRating", "3")
                  .param("cameraId", "1")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(1)))
          .andExpect(jsonPath("$.content[0].title", is("Multi-filter Image")))
          .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("Should return empty response when no images match filters")
    void searchImages_emptyResults_shouldReturnEmptyResponse() throws Exception {
      // Arrange
      ImageSearchResponse response = new ImageSearchResponse(List.of(), 0, 0);

      when(contentService.searchImages(any(ImageSearchRequest.class))).thenReturn(response);

      // Act & Assert
      mockMvc
          .perform(get("/api/read/content/images/search").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(0)))
          .andExpect(jsonPath("$.totalElements", is(0)))
          .andExpect(jsonPath("$.totalPages", is(0)));
    }
  }

  @Nested
  @DisplayName("GET /content/film-metadata")
  class GetFilmMetadata {

    @Test
    @DisplayName("Should return film types and film formats")
    void getFilmMetadata_shouldReturnFilmTypesAndFormats() throws Exception {
      // Arrange
      List<ContentFilmTypeModel> filmTypes =
          List.of(
              new ContentFilmTypeModel(1L, "KODAK_PORTRA_400", "Kodak Portra 400", 400, List.of()),
              new ContentFilmTypeModel(2L, "FUJI_SUPERIA_400", "Fuji Superia 400", 400, List.of()));

      when(metadataService.getAllFilmTypes()).thenReturn(filmTypes);

      // Act & Assert
      mockMvc
          .perform(get("/api/read/content/film-metadata").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.filmTypes", hasSize(2)))
          .andExpect(jsonPath("$.filmTypes[0].filmTypeName", is("KODAK_PORTRA_400")))
          .andExpect(jsonPath("$.filmTypes[0].displayName", is("Kodak Portra 400")))
          .andExpect(jsonPath("$.filmTypes[0].defaultIso", is(400)))
          .andExpect(jsonPath("$.filmTypes[1].filmTypeName", is("FUJI_SUPERIA_400")))
          .andExpect(jsonPath("$.filmFormats", hasSize(FilmFormat.values().length)))
          .andExpect(jsonPath("$.filmFormats[0].name", is("MM_35")))
          .andExpect(jsonPath("$.filmFormats[0].displayName", is("35mm")))
          .andExpect(jsonPath("$.filmFormats[1].name", is("MM_120")))
          .andExpect(jsonPath("$.filmFormats[1].displayName", is("120")));
    }

    @Test
    @DisplayName("Should return empty film types list with formats when no film types exist")
    void getFilmMetadata_emptyFilmTypes_shouldReturnEmptyListWithFormats() throws Exception {
      // Arrange
      when(metadataService.getAllFilmTypes()).thenReturn(List.of());

      // Act & Assert
      mockMvc
          .perform(get("/api/read/content/film-metadata").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.filmTypes", hasSize(0)))
          .andExpect(jsonPath("$.filmFormats", hasSize(FilmFormat.values().length)))
          .andExpect(jsonPath("$.filmFormats[0].name", is("MM_35")))
          .andExpect(jsonPath("$.filmFormats[1].name", is("MM_120")));
    }
  }
}
