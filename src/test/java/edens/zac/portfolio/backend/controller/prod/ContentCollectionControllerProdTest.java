package edens.zac.portfolio.backend.controller.prod;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.services.ContentCollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.persistence.EntityNotFoundException;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@ExtendWith(MockitoExtension.class)
class ContentCollectionControllerProdTest {

    private MockMvc mockMvc;

    @Mock
    private ContentCollectionService contentCollectionService;

    @InjectMocks
    private ContentCollectionControllerProd contentCollectionController;

    private ObjectMapper objectMapper;

    private List<ContentCollectionModel> testCollections;
    private ContentCollectionModel testCollection;

    @BeforeEach
    void setUp() {
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Set up MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(contentCollectionController).build();

        // Create test collections
        testCollections = new ArrayList<>();

        // Create a blog collection
        ContentCollectionModel blog = new ContentCollectionModel();
        blog.setId(1L);
        blog.setType(CollectionType.BLOG);
        blog.setTitle("Test Blog");
        blog.setSlug("test-blog");
        blog.setDescription("A test blog collection");
        blog.setVisible(true);
        blog.setPriority(1);
        blog.setBlocksPerPage(30);
        blog.setTotalBlocks(5);
        blog.setTotalPages(1);
        blog.setCurrentPage(0);
        blog.setContentBlocks(new ArrayList<>());
        blog.setCreatedAt(LocalDateTime.now());
        blog.setUpdatedAt(LocalDateTime.now());

        // Create an art gallery collection
        ContentCollectionModel artGallery = new ContentCollectionModel();
        artGallery.setId(2L);
        artGallery.setType(CollectionType.ART_GALLERY);
        artGallery.setTitle("Test Art Gallery");
        artGallery.setSlug("test-art-gallery");
        artGallery.setDescription("A test art gallery collection");
        artGallery.setVisible(true);
        artGallery.setPriority(2);
        artGallery.setBlocksPerPage(30);
        artGallery.setTotalBlocks(10);
        artGallery.setTotalPages(1);
        artGallery.setCurrentPage(0);
        artGallery.setContentBlocks(new ArrayList<>());
        artGallery.setCreatedAt(LocalDateTime.now());
        artGallery.setUpdatedAt(LocalDateTime.now());

        // Create a client gallery collection with password protection
        ContentCollectionModel clientGallery = new ContentCollectionModel();
        clientGallery.setId(3L);
        clientGallery.setType(CollectionType.CLIENT_GALLERY);
        clientGallery.setTitle("Test Client Gallery");
        clientGallery.setSlug("test-client-gallery");
        clientGallery.setDescription("A test client gallery collection");
        clientGallery.setVisible(true);
        clientGallery.setPriority(3);
        clientGallery.setBlocksPerPage(50);
        clientGallery.setTotalBlocks(100);
        clientGallery.setTotalPages(2);
        clientGallery.setCurrentPage(0);
        clientGallery.setContentBlocks(new ArrayList<>());
        clientGallery.setCreatedAt(LocalDateTime.now());
        clientGallery.setUpdatedAt(LocalDateTime.now());
        clientGallery.setIsPasswordProtected(true);

        testCollections.add(blog);
        testCollections.add(artGallery);
        testCollections.add(clientGallery);

        // Set the test collection for individual tests
        testCollection = blog;
    }

    @Test
    @DisplayName("GET /collections should return paginated collections")
    void getAllCollections_shouldReturnPaginatedCollections() throws Exception {
        // Arrange
        Page<ContentCollectionModel> page = new PageImpl<>(testCollections, PageRequest.of(0, 10), 3);
        when(contentCollectionService.getAllCollections(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/read/collections")
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
        Page<ContentCollectionModel> page = new PageImpl<>(testCollections, PageRequest.of(0, 10), 3);
        when(contentCollectionService.getAllCollections(any(Pageable.class))).thenReturn(page);

        // Act & Assert - Controller normalizes negative page to 0
        mockMvc.perform(get("/api/read/collections")
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
        when(contentCollectionService.getCollectionWithPagination(eq("test-blog"), anyInt(), anyInt()))
                .thenReturn(testCollection);

        // Act & Assert
        mockMvc.perform(get("/api/read/collections/test-blog")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Blog")))
                .andExpect(jsonPath("$.slug", is("test-blog")))
                .andExpect(jsonPath("$.type", is("BLOG")))
                .andExpect(jsonPath("$.totalBlocks", is(5)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.currentPage", is(0)));
    }

    @Test
    @DisplayName("GET /collections/{slug} with non-existent slug should return not found")
    void getCollectionBySlug_withNonExistentSlug_shouldReturnNotFound() throws Exception {
        // Arrange
        when(contentCollectionService.getCollectionWithPagination(eq("non-existent"), anyInt(), anyInt()))
                .thenThrow(new EntityNotFoundException("Collection with slug: non-existent not found"));

        // Act & Assert
        mockMvc.perform(get("/api/read/collections/non-existent")
                .param("page", "0")
                .param("size", "30")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));
    }

    @Test
    @DisplayName("GET /collections/{slug} with negative page should normalize to page 0")
    void getCollectionBySlug_withNegativePage_shouldNormalizeToZero() throws Exception {
        // Arrange
        when(contentCollectionService.getCollectionWithPagination(eq("test-blog"), eq(0), eq(30)))
                .thenReturn(testCollection);

        // Act & Assert - Controller normalizes negative page to 0
        mockMvc.perform(get("/api/read/collections/test-blog")
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
        List<edens.zac.portfolio.backend.model.HomeCardModel> homeCards = List.of(
                edens.zac.portfolio.backend.model.HomeCardModel.builder()
                        .id(1L)
                        .title("Test Blog")
                        .cardType("BLOG")
                        .slug("test-blog")
                        .build()
        );

        when(contentCollectionService.findVisibleByTypeOrderByDate(eq(CollectionType.BLOG)))
                .thenReturn(homeCards);

        // Act & Assert
        mockMvc.perform(get("/api/read/collections/type/BLOG")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Test Blog")))
                .andExpect(jsonPath("$[0].cardType", is("BLOG")));
    }

    @Test
    @DisplayName("GET /collections/type/{type} with invalid type should return bad request")
    void getCollectionsByType_withInvalidType_shouldReturnBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/read/collections/type/INVALID_TYPE")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", containsString("Invalid collection type")));
    }

    @Test
    @DisplayName("POST /collections/{slug}/access with correct password should return access granted")
    void validateClientGalleryAccess_withCorrectPassword_shouldReturnAccessGranted() throws Exception {
        // Arrange
        Map<String, String> passwordRequest = new HashMap<>();
        passwordRequest.put("password", "correct-password");

        when(contentCollectionService.validateClientGalleryAccess(eq("test-client-gallery"), eq("correct-password")))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccess", is(true)));
    }

    @Test
    @DisplayName("POST /collections/{slug}/access with incorrect password should return access denied")
    void validateClientGalleryAccess_withIncorrectPassword_shouldReturnAccessDenied() throws Exception {
        // Arrange
        Map<String, String> passwordRequest = new HashMap<>();
        passwordRequest.put("password", "wrong-password");

        when(contentCollectionService.validateClientGalleryAccess(eq("test-client-gallery"), eq("wrong-password")))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccess", is(false)));
    }

    @Test
    @DisplayName("POST /collections/{slug}/access without password should return bad request")
    void validateClientGalleryAccess_withoutPassword_shouldReturnBadRequest() throws Exception {
        // Arrange
        Map<String, String> emptyRequest = new HashMap<>();

        // Act & Assert
        mockMvc.perform(post("/api/read/collections/test-client-gallery/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", is("Password is required")));
    }

    @Test
    @DisplayName("POST /collections/{slug}/access with non-existent collection should return not found")
    void validateClientGalleryAccess_withNonExistentCollection_shouldReturnNotFound() throws Exception {
        // Arrange
        Map<String, String> passwordRequest = new HashMap<>();
        passwordRequest.put("password", "any-password");

        when(contentCollectionService.validateClientGalleryAccess(eq("non-existent"), anyString()))
                .thenThrow(new EntityNotFoundException("Collection with slug: non-existent not found"));

        // Act & Assert
        mockMvc.perform(post("/api/read/collections/non-existent/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));
    }
}
