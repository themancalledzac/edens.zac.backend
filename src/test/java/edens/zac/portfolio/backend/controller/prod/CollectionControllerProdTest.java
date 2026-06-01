package edens.zac.portfolio.backend.controller.prod;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.ClientGalleryAccessLimiter;
import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.PasswordRequest;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CollectionControllerProdTest {

  private MockMvc mockMvc;

  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private CollectionService collectionService;
  @Mock private ClientGalleryAccessLimiter accessLimiter;

  @InjectMocks private CollectionControllerProd contentCollectionController;

  private ObjectMapper objectMapper;

  private List<CollectionModel> testCollections;
  private CollectionModel testCollection;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    // @Value-injected fields are not populated by Mockito's @InjectMocks. Mirror the
    // production default (Secure=true) so the existing cookie().secure(true) assertion
    // covers the prod profile; a separate test below pins the dev-profile path.
    ReflectionTestUtils.setField(contentCollectionController, "galleryCookieSecure", true);

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
            .visibility(CollectionVisibility.LISTED)
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
            .visibility(CollectionVisibility.LISTED)
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
            .visibility(CollectionVisibility.LISTED)
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

  private CollectionModel createPasswordProtectedCollection() {
    return CollectionModel.builder()
        .id(3L)
        .title("Client Gallery")
        .slug("client-gallery")
        .type(CollectionType.CLIENT_GALLERY)
        .visibility(CollectionVisibility.LISTED)
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
    when(collectionService.getVisibleCollections(any(Pageable.class))).thenReturn(page);

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
        .andExpect(jsonPath("$.number", is(0)))
        .andExpect(jsonPath("$.last", is(true)))
        // Pin the wire shape: response uses our PagedResponse DTO, NOT Spring's PageImpl, which
        // would also leak pageable/sort/numberOfElements/empty and which Spring's
        // PageModule$WarningLoggingModifier warns about as unstable across versions.
        .andExpect(jsonPath("$.pageable").doesNotExist())
        .andExpect(jsonPath("$.sort").doesNotExist())
        .andExpect(jsonPath("$.numberOfElements").doesNotExist());
  }

  @Test
  @DisplayName("GET /collections with negative page should normalize to page 0")
  void getAllCollections_withNegativePage_shouldNormalizeToZero() throws Exception {
    // Arrange
    Page<CollectionModel> page = new PageImpl<>(testCollections, PageRequest.of(0, 10), 3);
    when(collectionService.getVisibleCollections(any(Pageable.class))).thenReturn(page);

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
  @DisplayName("GET /{slug} should serialize siblings in the response")
  void getCollectionBySlug_returnsSiblings() throws Exception {
    // Arrange
    CollectionModel model =
        CollectionModel.builder()
            .id(1L)
            .type(CollectionType.PORTFOLIO)
            .title("Dolomites")
            .slug("dolomites")
            .visibility(CollectionVisibility.LISTED)
            .siblings(
                List.of(
                    new Records.CollectionList(
                        2L, "Dolomites Film", "dolomites-film", CollectionType.PORTFOLIO)))
            .build();
    when(collectionService.getCollectionWithPagination(eq("dolomites"), anyInt(), anyInt()))
        .thenReturn(model);

    // Act & Assert
    mockMvc
        .perform(get("/api/read/collections/dolomites"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siblings", hasSize(1)))
        .andExpect(jsonPath("$.siblings[0].slug", is("dolomites-film")))
        .andExpect(jsonPath("$.siblings[0].name", is("Dolomites Film")));
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
  @DisplayName(
      "POST /collections/{slug}/access with correct password should set HttpOnly access cookie")
  void validateClientGalleryAccess_withCorrectPassword_shouldSetCookie() throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("correct-password");

    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("correct-password")))
        .thenReturn(true);
    when(clientGalleryAuthService.buildAccessCookies(
            eq("test-client-gallery"), eq("correct-password"), eq(true), any(Duration.class)))
        .thenReturn(
            List.of(
                ResponseCookie.from("gallery_access_test-client-gallery", "hmac-token|1234567890")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build()));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)))
        // The token must NOT be returned in the JSON body — it lives only in the cookie now.
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        // Cookie attributes
        .andExpect(cookie().exists("gallery_access_test-client-gallery"))
        .andExpect(cookie().value("gallery_access_test-client-gallery", "hmac-token|1234567890"))
        .andExpect(cookie().httpOnly("gallery_access_test-client-gallery", true))
        .andExpect(cookie().secure("gallery_access_test-client-gallery", true))
        .andExpect(cookie().path("gallery_access_test-client-gallery", "/"))
        .andExpect(cookie().maxAge("gallery_access_test-client-gallery", 24 * 60 * 60))
        // SameSite is not exposed via cookie() matchers; check the raw header
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
  }

  @Test
  @DisplayName(
      "POST /collections/{slug}/access also sets the shared password-fingerprint cookie for group unlock")
  void validateClientGalleryAccess_alsoSetsFingerprintCookie() throws Exception {
    PasswordRequest passwordRequest = new PasswordRequest("shared-pw");
    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("shared-pw")))
        .thenReturn(true);
    when(clientGalleryAuthService.buildAccessCookies(
            eq("test-client-gallery"), eq("shared-pw"), eq(true), any(Duration.class)))
        .thenReturn(
            List.of(
                ResponseCookie.from("gallery_access_test-client-gallery", "slug-token")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build(),
                ResponseCookie.from("gallery_access_pw_FINGERPRINT", "group-token")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build()));

    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)))
        .andExpect(cookie().exists("gallery_access_test-client-gallery"))
        .andExpect(cookie().exists("gallery_access_pw_FINGERPRINT"))
        .andExpect(cookie().value("gallery_access_pw_FINGERPRINT", "group-token"))
        .andExpect(cookie().httpOnly("gallery_access_pw_FINGERPRINT", true))
        .andExpect(cookie().secure("gallery_access_pw_FINGERPRINT", true))
        .andExpect(cookie().path("gallery_access_pw_FINGERPRINT", "/"))
        .andExpect(cookie().maxAge("gallery_access_pw_FINGERPRINT", 24 * 60 * 60));
  }

  @Test
  @DisplayName(
      "POST /collections/{slug}/access in dev profile (cookie-secure=false) should omit Secure attr")
  void validateClientGalleryAccess_devProfile_shouldOmitSecureCookieAttribute() throws Exception {
    // Localhost runs over plain http; browsers silently reject Secure cookies on
    // insecure origins, which would lock the gate after a successful submit. The
    // dev profile flips app.gallery-access.cookie-secure to false to keep the
    // cookie storable. Production must keep Secure on (covered by the test above).
    ReflectionTestUtils.setField(contentCollectionController, "galleryCookieSecure", false);

    PasswordRequest passwordRequest = new PasswordRequest("correct-password");
    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("correct-password")))
        .thenReturn(true);
    when(clientGalleryAuthService.buildAccessCookies(
            eq("test-client-gallery"), eq("correct-password"), eq(false), any(Duration.class)))
        .thenReturn(
            List.of(
                ResponseCookie.from("gallery_access_test-client-gallery", "hmac-token|1234567890")
                    .httpOnly(true)
                    .secure(false)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build()));

    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)))
        .andExpect(cookie().exists("gallery_access_test-client-gallery"))
        .andExpect(cookie().httpOnly("gallery_access_test-client-gallery", true))
        .andExpect(cookie().secure("gallery_access_test-client-gallery", false))
        .andExpect(header().string("Set-Cookie", not(containsString("Secure"))))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
  }

  @Test
  @DisplayName(
      "POST /collections/{slug}/access with incorrect password should return access denied without cookie")
  void validateClientGalleryAccess_withIncorrectPassword_shouldReturnAccessDeniedNoCookie()
      throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("wrong-password");

    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("wrong-password")))
        .thenReturn(false);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(false)))
        .andExpect(cookie().doesNotExist("gallery_access_test-client-gallery"));
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

    when(accessLimiter.allow(anyString(), eq("non-existent"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(eq("non-existent"), anyString()))
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
  @DisplayName("POST /collections/{slug}/access exceeding rate limit should return 429")
  void validateClientGalleryAccess_rateLimited_shouldReturn429() throws Exception {
    // Arrange
    PasswordRequest passwordRequest = new PasswordRequest("any-password");

    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(false);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.hasAccess", is(false)))
        .andExpect(jsonPath("$.reason", is("rate-limited")))
        .andExpect(cookie().doesNotExist("gallery_access_test-client-gallery"));
  }

  @Test
  @DisplayName("GET /collections/location/{slug} should return collections and orphan images")
  void getLocationPage_shouldReturnCollectionsAndOrphanImages() throws Exception {
    // Arrange
    Records.Location location = new Records.Location(1L, "Seattle", "seattle");

    CollectionModel collection =
        CollectionModel.builder()
            .id(1L)
            .title("Seattle Trip")
            .slug("seattle-trip")
            .type(CollectionType.PORTFOLIO)
            .visibility(CollectionVisibility.LISTED)
            .build();

    ContentModels.Image image = createStubImage(10L, "Sunset");

    LocationPageResponse response =
        new LocationPageResponse(location, List.of(collection), List.of(image), 1L, 1L);

    when(collectionService.getLocationPageBySlug(
            eq("seattle"), anyInt(), anyInt(), anyInt(), anyInt()))
        .thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/location/seattle").contentType(MediaType.APPLICATION_JSON))
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
            .visibility(CollectionVisibility.LISTED)
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
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any()))
        .thenReturn(false);

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
  @DisplayName("GET /collections/{slug} password protected with invalid cookie should omit content")
  void getCollectionBySlug_passwordProtected_invalidCookie_shouldOmitContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any()))
        .thenReturn(false);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .cookie(new Cookie("gallery_access_client-gallery", "wrong-token"))
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
            .visibility(CollectionVisibility.LISTED)
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
  @DisplayName(
      "GET /collections/{slug} password protected with valid cookie should include content")
  void getCollectionBySlug_passwordProtected_validCookie_shouldIncludeContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any())).thenReturn(true);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .cookie(new Cookie("gallery_access_client-gallery", "cookie-token"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Client Gallery")))
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.contentCount", is(5)));
  }

  @Test
  @DisplayName(
      "GET /collections/{slug} password-protected, group cookie via shared password, includes content")
  void getCollectionBySlug_passwordProtected_groupCookie_shouldIncludeContent() throws Exception {
    // Sibling gallery scenario: viewer unlocked the parent (or another sibling) and now visits
    // this gallery directly. Per the gated-yard model, isGalleryAccessAuthorized returns true
    // because the fingerprint cookie matches this gallery's password.
    CollectionModel protectedCollection = createPasswordProtectedCollection();
    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any())).thenReturn(true);

    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .cookie(new Cookie("gallery_access_pw_FINGERPRINT", "group-token"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.contentCount", is(5)));
  }

  @Test
  @DisplayName("GET /collections/{slug} password protected with no cookie strips content")
  void getCollectionBySlug_passwordProtected_noCookie_stripsContent() throws Exception {
    // Arrange
    CollectionModel protectedCollection = createPasswordProtectedCollection();

    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(protectedCollection);
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any()))
        .thenReturn(false);

    // Act & Assert — no cookie attached
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

  // ============================================================================
  // BE-H5: coverImage must be stripped for protected galleries without a valid cookie
  // ============================================================================

  private CollectionModel createPasswordProtectedCollectionWithCover() {
    CollectionModel model = createPasswordProtectedCollection();
    model.setCoverImage(createStubImage(42L, "cover"));
    return model;
  }

  @Test
  @DisplayName("BE-H5: GET /collections/{slug} protected, no cookie, retains coverImage")
  void getCollectionBySlug_protectedNoCookie_retainsCoverImage() throws Exception {
    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(createPasswordProtectedCollectionWithCover());

    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.coverImage.id", is(42)));
  }

  @Test
  @DisplayName("BE-H5: GET /collections/{slug} protected, invalid cookie, retains coverImage")
  void getCollectionBySlug_protectedInvalidCookie_retainsCoverImage() throws Exception {
    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(createPasswordProtectedCollectionWithCover());
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any()))
        .thenReturn(false);

    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .cookie(new Cookie("gallery_access_client-gallery", "bad"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.coverImage.id", is(42)));
  }

  @Test
  @DisplayName("BE-H5: GET /collections/{slug} protected, valid cookie, retains coverImage")
  void getCollectionBySlug_protectedValidCookie_retainsCoverImage() throws Exception {
    when(collectionService.getCollectionWithPagination(eq("client-gallery"), anyInt(), anyInt()))
        .thenReturn(createPasswordProtectedCollectionWithCover());
    when(collectionService.isGalleryAccessAuthorized(eq("client-gallery"), any())).thenReturn(true);

    mockMvc
        .perform(
            get("/api/read/collections/client-gallery")
                .param("page", "0")
                .param("size", "30")
                .cookie(new Cookie("gallery_access_client-gallery", "good"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.coverImage.id", is(42)))
        .andExpect(jsonPath("$.coverImage.title", is("cover")));
  }

  // ============================================================================
  // Fix 2: resolveClientIp - X-Forwarded-For without X-Real-IP uses getRemoteAddr
  // ============================================================================

  @Test
  @DisplayName(
      "POST /collections/{slug}/access with X-Forwarded-For but no X-Real-IP uses remote addr for rate limiting")
  void resolveClientIp_xForwardedForWithoutXRealIP_returnsRemoteAddr() throws Exception {
    // Arrange: set up rate limiter to always allow, then accept password
    PasswordRequest passwordRequest = new PasswordRequest("correct-password");
    when(accessLimiter.allow(anyString(), eq("test-client-gallery"))).thenReturn(true);
    when(clientGalleryAuthService.validateClientGalleryAccess(
            eq("test-client-gallery"), eq("correct-password")))
        .thenReturn(true);
    when(clientGalleryAuthService.buildAccessCookies(
            eq("test-client-gallery"), eq("correct-password"), eq(true), any(Duration.class)))
        .thenReturn(
            List.of(
                ResponseCookie.from("gallery_access_test-client-gallery", "some-token")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build()));

    // Act: send with only X-Forwarded-For, no X-Real-IP
    // The controller must fall through to getRemoteAddr() ("127.0.0.1" in MockMvc)
    // and the rate limiter must receive that address, not the spoofed "1.2.3.4"
    mockMvc
        .perform(
            post("/api/read/collections/test-client-gallery/access")
                .header("X-Forwarded-For", "1.2.3.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAccess", is(true)));

    // Verify the rate limiter was called with the actual remote address (not the spoofed header)
    // MockMvc uses "127.0.0.1" as the remote address
    org.mockito.Mockito.verify(accessLimiter).allow("127.0.0.1", "test-client-gallery");
  }

  // ============================================================================
  // Fix 1: coverImage stripped for protected CLIENT_GALLERY on list endpoints
  // The central fix is in CollectionProcessingUtil.buildBasicModel; these tests
  // verify the controller passes through the already-stripped model unchanged.
  // ============================================================================

  @Test
  @DisplayName(
      "GET /collections - password-protected CLIENT_GALLERY returned by service has null coverImage")
  void getAllCollections_protectedClientGallery_returnNullCoverImage() throws Exception {
    // Service returns a model whose coverImage was already stripped by buildBasicModel
    CollectionModel protectedGallery =
        CollectionModel.builder()
            .id(10L)
            .type(CollectionType.CLIENT_GALLERY)
            .title("Protected Gallery")
            .slug("protected-gallery")
            .visibility(CollectionVisibility.LISTED)
            .isPasswordProtected(true)
            .coverImage(null) // stripped by CollectionProcessingUtil.buildBasicModel
            .contentCount(5)
            .totalPages(1)
            .currentPage(0)
            .build();

    Page<CollectionModel> page =
        new PageImpl<>(List.of(protectedGallery), PageRequest.of(0, 50), 1);
    when(collectionService.getVisibleCollections(any(Pageable.class))).thenReturn(page);

    mockMvc
        .perform(get("/api/read/collections").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].type", is("CLIENT_GALLERY")))
        .andExpect(jsonPath("$.content[0].isPasswordProtected", is(true)))
        .andExpect(jsonPath("$.content[0].coverImage").doesNotExist());
  }
}
