package edens.zac.portfolio.backend.controller.prod;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.PasswordRequest;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CollectionControllerProdTest {

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;

  @InjectMocks private CollectionControllerProd contentCollectionController;

  private ObjectMapper objectMapper;

  private List<CollectionModel> testCollections;
  private CollectionModel testCollection;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    mockMvc =
        MockMvcBuilders.standaloneSetup(contentCollectionController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    CollectionModel blog =
        CollectionModel.builder()
            .id(1L)
            .type(CollectionType.BLOG)
            .title("Test Blog")
            .slug("test-blog")
            .visible(true)
            .contentCount(5)
            .totalPages(1)
            .currentPage(0)
            .content(new ArrayList<>())
            .build();

    CollectionModel artGallery =
        CollectionModel.builder()
            .id(2L)
            .type(CollectionType.ART_GALLERY)
            .title("Test Art Gallery")
            .slug("test-art-gallery")
            .visible(true)
            .contentCount(10)
            .totalPages(1)
            .currentPage(0)
            .content(new ArrayList<>())
            .build();

    CollectionModel clientGallery =
        CollectionModel.builder()
            .id(3L)
            .type(CollectionType.CLIENT_GALLERY)
            .title("Test Client Gallery")
            .slug("test-client-gallery")
            .visible(true)
            .contentCount(100)
            .totalPages(2)
            .currentPage(0)
            .content(new ArrayList<>())
            .build();

    testCollections = List.of(blog, artGallery, clientGallery);
    testCollection = blog;
  }

  private ContentModels.Image createStubImage(Long id, String title) {
    return new ContentModels.Image(
        id,
        null,
        title,
        null,
        "https://example.com/stub.jpg",
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

  private CollectionModel createPasswordProtectedCollection() {
    return CollectionModel.builder()
        .id(3L)
        .title("Client Gallery")
        .slug("client-gallery")
        .type(CollectionType.CLIENT_GALLERY)
        .visible(true)
        .isPasswordProtected(true)
        .contentCount(5)
        .content(new ArrayList<>(List.of(createStubImage(99L, "Gallery Image"))))
        .build();
  }

  @Test
  @DisplayName("GET /collections should return paginated collections")
  void getAllCollections_shouldReturnPaginatedCollections() throws Exception {
    // Arrange
    Page<CollectionModel> page = new PageImpl<>(testCollections, PageRequest.of(0, 10), 3);
    when(collectionService.getAllCollections(any(Pageable.class))).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].title", is("Test Blog")))
        .andExpect(jsonPath("$.content[1].title", is("Test Art Gallery")))
        .andExpect(jsonPath("$.content[2].title", is("Test Client Gallery")))
        .andExpect(jsonPath("$.totalElements", is(3)))
        .andExpect(jsonPath("$.totalPages", is(1)))
        .andExpect(jsonPath("$.number", is(0)));
  }

  @Test
  @DisplayName("GET /collections with negative page should normalize to page 0")
  void getAllCollections_withNegativePage_shouldNormalizeToZero() throws Exception {
    // Arrange
    Page<CollectionModel> page = new PageImpl<>(testCollections, PageRequest.of(0, 10), 3);
    when(collectionService.getAllCollections(any(Pageable.class))).thenReturn(page);

    // Act & Assert - Controller normalizes negative page to 0
    mockMvc
        .perform(
            get("/api/read/collections")
                .param("page", "-1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.number", is(0)));
  }

  @Test
  @DisplayName("GET /collections/{slug} should return collection with paginated content")
  void getCollectionBySlug_shouldReturnCollectionWithPaginatedContent() throws Exception {
    // Arrange
    when(collectionService.getCollectionWithPagination(eq("test-blog"), anyInt(), anyInt()))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/test-blog")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")))
        .andExpect(jsonPath("$.slug", is("test-blog")))
        .andExpect(jsonPath("$.type", is("BLOG")))
        .andExpect(jsonPath("$.contentCount", is(5)))
        .andExpect(jsonPath("$.totalPages", is(1)))
        .andExpect(jsonPath("$.currentPage", is(0)));
  }

  @Test
  @DisplayName("GET /collections/{slug} with non-existent slug should return not found")
  void getCollectionBySlug_withNonExistentSlug_shouldReturnNotFound() throws Exception {
    // Arrange
    when(collectionService.getCollectionWithPagination(eq("non-existent"), anyInt(), anyInt()))
        .thenThrow(new ResourceNotFoundException("Collection not found with slug: non-existent"));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/non-existent")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("not found")));
  }

  @Test
  @DisplayName("GET /collections/{slug} with negative page should normalize to page 0")
  void getCollectionBySlug_withNegativePage_shouldNormalizeToZero() throws Exception {
    // Arrange
    when(collectionService.getCollectionWithPagination(eq("test-blog"), eq(0), eq(30)))
        .thenReturn(testCollection);

    // Act & Assert - Controller normalizes negative page to 0
    mockMvc
        .perform(
            get("/api/read/collections/test-blog")
                .param("page", "-1")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug", is("test-blog")))
        .andExpect(jsonPath("$.currentPage", is(0)));
  }

  @Test
  @DisplayName("GET /collections/type/{type} should return collections of specified type")
  void getCollectionsByType_shouldReturnCollectionsOfSpecifiedType() throws Exception {
    // Arrange
    CollectionModel blogCollection =
        CollectionModel.builder()
            .id(1L)
            .title("Test Blog")
            .type(CollectionType.BLOG)
            .slug("test-blog")
            .visible(true)
            .build();

    List<CollectionModel> collections = List.of(blogCollection);

    when(collectionService.findVisibleByTypeOrderByDate(eq(CollectionType.BLOG)))
        .thenReturn(collections);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/collections/type/BLOG").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title", is("Test Blog")))
        .andExpect(jsonPath("$[0].type", is("BLOG")));
  }

  @Test
  @DisplayName("GET /collections/type/PORTFOLIO should return portfolio collections")
  void getCollectionsByType_withPortfolioType_shouldReturnPortfolioCollections() throws Exception {
    // Arrange
    CollectionModel portfolio =
        CollectionModel.builder()
            .id(1L)
            .title("Test Portfolio")
            .type(CollectionType.PORTFOLIO)
            .slug("test-portfolio")
            .visible(true)
            .build();

    when(collectionService.findVisibleByTypeOrderByDate(eq(CollectionType.PORTFOLIO)))
        .thenReturn(List.of(portfolio));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/type/PORTFOLIO").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title", is("Test Portfolio")))
        .andExpect(jsonPath("$[0].type", is("PORTFOLIO")));
  }

  @Test
  @DisplayName("GET /collections/type/CLIENT_GALLERY should return client gallery collections")
  void getCollectionsByType_withClientGalleryType_shouldReturnClientGalleryCollections()
      throws Exception {
    // Arrange
    CollectionModel gallery =
        CollectionModel.builder()
            .id(1L)
            .title("Wedding Gallery")
            .type(CollectionType.CLIENT_GALLERY)
            .slug("wedding-gallery")
            .visible(true)
            .isPasswordProtected(false)
            .build();

    when(collectionService.findVisibleByTypeOrderByDate(eq(CollectionType.CLIENT_GALLERY)))
        .thenReturn(List.of(gallery));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/type/CLIENT_GALLERY")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].type", is("CLIENT_GALLERY")))
        .andExpect(jsonPath("$[0].isPasswordProtected", is(false)));
  }

  @Test
  @DisplayName("GET /collections/type/{type} with invalid type should return bad request")
  void getCollectionsByType_withInvalidType_shouldReturnBadRequest() throws Exception {
    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/type/INVALID_TYPE")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Invalid collection type")));
  }

  @Test
  @DisplayName("POST /collections/{slug}/access with correct password should return access granted")
  void validateClientGalleryAccess_withCorrectPassword_shouldReturnAccessGranted()
      throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("correct-password");

    when(collectionService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("correct-password")))
        .thenReturn(true);
    when(collectionService.generateAccessToken(eq("test-client-gallery")))
        .thenReturn("mock-token|12345");

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)));
  }

  @Test
  @DisplayName(
      "POST /collections/{slug}/access with incorrect password should return access denied")
  void validateClientGalleryAccess_withIncorrectPassword_shouldReturnAccessDenied()
      throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("wrong-password");

    when(collectionService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("wrong-password")))
        .thenReturn(false);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(false)));
  }

  @Test
  @DisplayName("POST /collections/{slug}/access without password should return bad request")
  void validateClientGalleryAccess_withoutPassword_shouldReturnBadRequest() throws Exception {
    // Arrange - send JSON with blank password to trigger @NotBlank validation
    String blankPasswordJson = "{\"password\": \"\"}";

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(blankPasswordJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Password is required")));
  }

  @Test
  @DisplayName(
      "POST /collections/{slug}/access with non-existent collection should return not found")
  void validateClientGalleryAccess_withNonExistentCollection_shouldReturnNotFound()
      throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("any-password");

    when(collectionService.validateClientGalleryAccess(eq("non-existent"), anyString()))
        .thenThrow(new ResourceNotFoundException("Collection not found with slug: non-existent"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/non-existent/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("not found")));
  }

  @Test
  @DisplayName("GET /collections/location/{name} should return collections and orphan images")
  void getLocationPage_shouldReturnCollectionsAndOrphanImages() throws Exception {
    // Arrange
    Records.Location location = new Records.Location(1L, "Seattle");

    CollectionModel collection =
        CollectionModel.builder()
            .id(1L)
            .title("Seattle Trip")
            .slug("seattle-trip")
            .type(CollectionType.PORTFOLIO)
            .visible(true)
            .build();

    ContentModels.Image image = createStubImage(10L, "Sunset");

    LocationPageResponse response =
        new LocationPageResponse(location, List.of(collection), List.of(image), 1L, 1L);

    when(collectionService.getLocationPage(eq("Seattle"), anyInt(), anyInt(), anyInt(), anyInt()))
        .thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/location/Seattle").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location.id", is(1)))
        .andExpect(jsonPath("$.location.name", is("Seattle")))
        .andExpect(jsonPath("$.collections", hasSize(1)))
        .andExpect(jsonPath("$.collections[0].title", is("Seattle Trip")))
        .andExpect(jsonPath("$.images", hasSize(1)))
        .andExpect(jsonPath("$.totalCollections", is(1)))
        .andExpect(jsonPath("$.totalImages", is(1)));
  }

  @Test
  @DisplayName("GET /collections/{slug}/meta should return metadata only")
  void getCollectionMeta_shouldReturnMetadataOnly() throws Exception {
    // Arrange
    CollectionModel metaModel =
        CollectionModel.builder()
            .id(1L)
            .title("Test Blog")
            .slug("test-blog")
            .type(CollectionType.BLOG)
            .visible(true)
            .build();

    when(collectionService.findMetaBySlug("test-blog")).thenReturn(metaModel);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/test-blog/meta").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Test Blog")))
        .andExpect(jsonPath("$.slug", is("test-blog")))
        .andExpect(jsonPath("$.content").doesNotExist());
  }

  @Test
  @DisplayName("GET /collections/{slug}/meta with non-existent slug should return 404")
  void getCollectionMeta_nonExistentSlug_shouldReturn404() throws Exception {
    // Arrange
    when(collectionService.findMetaBySlug("non-existent"))
        .thenThrow(new ResourceNotFoundException("Collection not found with slug: non-existent"));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/non-existent/meta").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("not found")));
  }

  @Test
  @DisplayName("GET /collections/{slug} password protected without token should omit content")
  void getCollectionBySlug_passwordProtected_noToken_shouldOmitContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Client Gallery")))
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.content").doesNotExist())
        .andExpect(jsonPath("$.contentCount").doesNotExist());
  }

  @Test
  @DisplayName("GET /collections/{slug} password protected with valid token should include content")
  void getCollectionBySlug_passwordProtected_validToken_shouldIncludeContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.validateAccessToken(eq("client-gallery"), eq("valid-token")))
        .thenReturn(true);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .param("accessToken", "valid-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Client Gallery")))
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.contentCount", is(5)));
  }

  @Test
  @DisplayName("GET /collections/{slug} password protected with invalid token should omit content")
  void getCollectionBySlug_passwordProtected_invalidToken_shouldOmitContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.validateAccessToken(eq("client-gallery"), eq("wrong-token")))
        .thenReturn(false);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .param("accessToken", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Client Gallery")))
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.content").doesNotExist())
        .andExpect(jsonPath("$.contentCount").doesNotExist());
  }

  @Test
  @DisplayName(
      "GET /collections/{slug} not password protected should include content without token")
  void getCollectionBySlug_notPasswordProtected_shouldIncludeContent() throws Exception {
    // Arrange
    CollectionModel publicCollection =
        CollectionModel.builder()
            .id(1L)
            .title("Public Gallery")
            .slug("public-gallery")
            .type(CollectionType.PORTFOLIO)
            .visible(true)
            .isPasswordProtected(false)
            .content(new ArrayList<>(List.of(createStubImage(99L, "Stub"))))
            .contentCount(5)
            .build();

    when(collectionService.getCollectionWithPagination(eq("public-gallery"), anyInt(), anyInt()))
        .thenReturn(publicCollection);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/public-gallery")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Public Gallery")))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.contentCount", is(5)));
  }

  @Test
  @DisplayName("POST /collections/{slug}/access with correct password should return access token")
  void validateClientGalleryAccess_success_shouldReturnAccessToken() throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("correct-password");

    when(collectionService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("correct-password")))
        .thenReturn(true);
    when(collectionService.generateAccessToken(eq("test-client-gallery")))
        .thenReturn("hmac-token|1234567890");

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)))
        .andExpect(jsonPath("$.accessToken", is("hmac-token|1234567890")));
  }
}
