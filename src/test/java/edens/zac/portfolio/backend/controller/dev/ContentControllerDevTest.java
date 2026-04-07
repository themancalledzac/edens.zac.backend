package edens.zac.portfolio.backend.controller.dev;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.ImageUploadPipelineService;
import edens.zac.portfolio.backend.services.JobTrackingService;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContentControllerDevTest {

  private MockMvc mockMvc;

  @Mock private ContentService contentService;

  @Mock private ImageUploadPipelineService imageUploadPipelineService;

  @Mock private JobTrackingService jobTrackingService;

  @InjectMocks private ContentControllerDev contentController;

  private ObjectMapper objectMapper;

  private List<ContentModels.Image> testImages;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    mockMvc =
        MockMvcBuilders.standaloneSetup(contentController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    ContentModels.Image testImage =
        new ContentModels.Image(
            1L,
            ContentType.IMAGE,
            "Test Image",
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
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of());

    testImages = List.of(testImage);
  }

  // ============== PATCH /api/admin/content/images ==============

  @Test
  @DisplayName("PATCH /content/images should update images with tags")
  void updateImages_shouldUpdateImagesWithTags() throws Exception {
    // Arrange
    CollectionRequests.TagUpdate tagUpdate =
        new CollectionRequests.TagUpdate(
            List.of(1L, 2L), List.of("landscape", "nature"), List.of(3L));

    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder()
            .id(1L)
            .title("Updated Image with Tags")
            .tags(tagUpdate)
            .build();

    Map<String, Object> response = new HashMap<>();
    response.put("updatedImages", testImages);
    response.put("newTags", new ArrayList<>());
    response.put("newPeople", new ArrayList<>());

    when(contentService.updateImages(any(List.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedImages", notNullValue()))
        .andExpect(jsonPath("$.updatedImages", hasSize(1)))
        .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

    verify(contentService).updateImages(any(List.class));
  }

  @Test
  @DisplayName("PATCH /content/images should update images with people")
  void updateImages_shouldUpdateImagesWithPeople() throws Exception {
    // Arrange
    CollectionRequests.PersonUpdate personUpdate =
        new CollectionRequests.PersonUpdate(
            List.of(5L, 6L), List.of("John Doe", "Jane Smith"), List.of(7L));

    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder()
            .id(1L)
            .title("Updated Image with People")
            .people(personUpdate)
            .build();

    Map<String, Object> response = new HashMap<>();
    response.put("updatedImages", testImages);
    response.put("newTags", new ArrayList<>());
    response.put("newPeople", new ArrayList<>());

    when(contentService.updateImages(any(List.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedImages", notNullValue()))
        .andExpect(jsonPath("$.updatedImages", hasSize(1)))
        .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

    verify(contentService).updateImages(anyList());
  }

  @Test
  @DisplayName("PATCH /content/images should update images with camera/lens/filmType metadata")
  void updateImages_shouldUpdateImagesWithCameraLensFilmType() throws Exception {
    // Arrange
    ContentImageUpdateRequest.CameraUpdate cameraUpdate =
        ContentImageUpdateRequest.CameraUpdate.builder().newValue("Canon AE-1").build();

    ContentImageUpdateRequest.LensUpdate lensUpdate =
        ContentImageUpdateRequest.LensUpdate.builder().newValue("50mm f/1.8").build();

    ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate =
        ContentImageUpdateRequest.FilmTypeUpdate.builder().prev(1L).build();

    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder()
            .id(1L)
            .title("Updated Image with Metadata")
            .camera(cameraUpdate)
            .lens(lensUpdate)
            .filmType(filmTypeUpdate)
            .iso(200)
            .build();

    Map<String, Object> response = new HashMap<>();
    response.put("updatedImages", testImages);
    response.put("newTags", new ArrayList<>());
    response.put("newPeople", new ArrayList<>());

    when(contentService.updateImages(any(List.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedImages", notNullValue()))
        .andExpect(jsonPath("$.updatedImages", hasSize(1)))
        .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

    verify(contentService).updateImages(any(List.class));
  }

  @Test
  @DisplayName("PATCH /content/images should update images with all metadata combined")
  void updateImages_shouldUpdateImagesWithAllMetadata() throws Exception {
    // Arrange
    CollectionRequests.TagUpdate tagUpdate =
        new CollectionRequests.TagUpdate(List.of(1L), List.of("travel"), List.of());

    CollectionRequests.PersonUpdate personUpdate =
        new CollectionRequests.PersonUpdate(List.of(5L), List.of("Alice"), List.of());

    ContentImageUpdateRequest.CameraUpdate cameraUpdate =
        ContentImageUpdateRequest.CameraUpdate.builder().newValue("Nikon F3").build();

    ContentImageUpdateRequest.LensUpdate lensUpdate =
        ContentImageUpdateRequest.LensUpdate.builder().newValue("85mm f/1.4").build();

    ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate =
        ContentImageUpdateRequest.FilmTypeUpdate.builder().prev(2L).build();

    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder()
            .id(1L)
            .title("Updated Image with All Metadata")
            .tags(tagUpdate)
            .people(personUpdate)
            .camera(cameraUpdate)
            .lens(lensUpdate)
            .filmType(filmTypeUpdate)
            .iso(400)
            .build();

    Map<String, Object> response = new HashMap<>();
    response.put("updatedImages", testImages);
    response.put("newTags", new ArrayList<>());
    response.put("newPeople", new ArrayList<>());

    when(contentService.updateImages(any(List.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedImages", notNullValue()))
        .andExpect(jsonPath("$.updatedImages", hasSize(1)))
        .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

    verify(contentService).updateImages(any(List.class));
  }

  @Test
  @DisplayName("PATCH /content/images should handle invalid request")
  void updateImages_shouldHandleInvalidRequest() throws Exception {
    // Arrange
    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder().id(999L).title("Non-existent Image").build();

    when(contentService.updateImages(any(List.class)))
        .thenThrow(new ResourceNotFoundException("Image not found with ID: 999"));

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("Image not found")));

    verify(contentService).updateImages(any(List.class));
  }

  @Test
  @DisplayName("PATCH /content/images should handle general errors")
  void updateImages_shouldHandleGeneralErrors() throws Exception {
    // Arrange
    ContentImageUpdateRequest updateRequest =
        ContentImageUpdateRequest.builder().id(1L).title("Test Image").build();

    when(contentService.updateImages(any(List.class)))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(
            patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

    verify(contentService).updateImages(anyList());
  }

  // ============== POST /api/admin/content/images/{collectionId} ==============

  @Test
  @DisplayName("POST /content/images/{collectionId} should create images in collection")
  void createImages_shouldCreateImagesInCollection() throws Exception {
    // Arrange
    MockMultipartFile file1 =
        new MockMultipartFile("files", "photo1.jpg", MediaType.IMAGE_JPEG_VALUE, "img1".getBytes());
    MockMultipartFile file2 =
        new MockMultipartFile("files", "photo2.jpg", MediaType.IMAGE_JPEG_VALUE, "img2".getBytes());

    ImageUploadResult uploadResult = new ImageUploadResult(testImages, List.of(), List.of());

    when(imageUploadPipelineService.createImagesParallel(eq(1L), anyList(), anyMap()))
        .thenReturn(uploadResult);

    // Act & Assert
    mockMvc
        .perform(multipart("/api/admin/content/images/{collectionId}", 1L).file(file1).file(file2))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.successful", hasSize(1)))
        .andExpect(jsonPath("$.successful[0].id", is(1)))
        .andExpect(jsonPath("$.failed", hasSize(0)));

    verify(imageUploadPipelineService).createImagesParallel(eq(1L), anyList(), anyMap());
    verify(contentService, never()).setCollectionLocationsIfMissing(any(), any());
  }

  // ============== POST /api/admin/content/images/create-collection ==============

  @Test
  @DisplayName("POST /content/images/create-collection should create collection and upload images")
  void createCollectionWithImages_shouldCreateCollectionAndUploadImages() throws Exception {
    // Arrange
    MockMultipartFile file =
        new MockMultipartFile("files", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());

    ImageUploadResult uploadResult = new ImageUploadResult(42L, testImages, List.of(), List.of());

    when(imageUploadPipelineService.createCollectionWithImages(
            any(CollectionRequests.Create.class), anyList(), anyMap()))
        .thenReturn(uploadResult);

    // Act & Assert
    mockMvc
        .perform(
            multipart("/api/admin/content/images/create-collection")
                .file(file)
                .param("title", "Test Collection")
                .param("type", "BLOG"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.collectionId", is(42)))
        .andExpect(jsonPath("$.successful", hasSize(1)))
        .andExpect(jsonPath("$.successful[0].id", is(1)));

    verify(imageUploadPipelineService)
        .createCollectionWithImages(any(CollectionRequests.Create.class), anyList(), anyMap());
  }

  // ============== POST /api/admin/content/images/{collectionId}/from-disk ==============

  @Test
  @DisplayName("POST /content/images/{collectionId}/from-disk should accept and return job ID")
  void createImagesFromDisk_shouldAcceptAndReturnJobId() throws Exception {
    // Arrange
    UUID jobId = UUID.randomUUID();
    JobTrackingService.JobStatus jobStatus = mock(JobTrackingService.JobStatus.class);
    when(jobStatus.jobId()).thenReturn(jobId);
    when(jobStatus.totalFiles()).thenReturn(3);

    when(imageUploadPipelineService.processFilesFromDisk(eq(5L), any(DiskUploadRequest.class)))
        .thenReturn(jobStatus);

    DiskUploadRequest request =
        new DiskUploadRequest(
            List.of(
                new DiskUploadRequest.FileEntry("/tmp/photo1.jpg", null, null),
                new DiskUploadRequest.FileEntry("/tmp/photo2.jpg", null, null),
                new DiskUploadRequest.FileEntry("/tmp/photo3.jpg", "/tmp/photo3.nef", null)),
            null);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/images/{collectionId}/from-disk", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId", is(jobId.toString())))
        .andExpect(jsonPath("$.totalFiles", is(3)))
        .andExpect(jsonPath("$.message", is("Processing started")));

    verify(imageUploadPipelineService).processFilesFromDisk(eq(5L), any(DiskUploadRequest.class));
  }

  // ============== GET /api/admin/content/images/jobs/{jobId} ==============

  @Test
  @DisplayName("GET /content/images/jobs/{jobId} should return job status")
  void getJobStatus_shouldReturnJobStatus() throws Exception {
    // Arrange
    UUID jobId = UUID.randomUUID();
    JobTrackingService.JobStatusResponse response =
        new JobTrackingService.JobStatusResponse(jobId, "PROCESSING", 5, 3, 2, 1, 0, List.of());

    when(jobTrackingService.getJob(jobId)).thenReturn(Optional.of(response));

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/content/images/jobs/{jobId}", jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId", is(jobId.toString())))
        .andExpect(jsonPath("$.status", is("PROCESSING")))
        .andExpect(jsonPath("$.totalFiles", is(5)))
        .andExpect(jsonPath("$.processed", is(3)));

    verify(jobTrackingService).getJob(jobId);
  }

  @Test
  @DisplayName("GET /content/images/jobs/{jobId} should return 404 when not found")
  void getJobStatus_shouldReturn404WhenNotFound() throws Exception {
    // Arrange
    UUID jobId = UUID.randomUUID();
    when(jobTrackingService.getJob(jobId)).thenReturn(Optional.empty());

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/content/images/jobs/{jobId}", jobId))
        .andExpect(status().isNotFound());

    verify(jobTrackingService).getJob(jobId);
  }

  // ============== POST /api/admin/content/tags ==============

  @Test
  @DisplayName("POST /content/tags should create a new tag")
  void createTag_shouldCreateNewTag() throws Exception {
    // Arrange
    ContentRequests.CreateTag request = new ContentRequests.CreateTag("landscape");

    Map<String, Object> response = new HashMap<>();
    response.put("tag", new Records.Tag(1L, "landscape", "landscape"));
    response.put("message", "Tag created successfully");

    when(contentService.createTag("landscape")).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tag", notNullValue()))
        .andExpect(jsonPath("$.message", is("Tag created successfully")));

    verify(contentService).createTag("landscape");
  }

  @Test
  @DisplayName("POST /content/tags should handle duplicate tag error")
  void createTag_shouldHandleDuplicateTagError() throws Exception {
    // Arrange
    ContentRequests.CreateTag request = new ContentRequests.CreateTag("landscape");

    when(contentService.createTag("landscape"))
        .thenThrow(new IllegalArgumentException("Tag already exists: landscape"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Tag already exists")));

    verify(contentService).createTag("landscape");
  }

  @Test
  @DisplayName("POST /content/tags should handle general errors")
  void createTag_shouldHandleGeneralErrors() throws Exception {
    // Arrange
    ContentRequests.CreateTag request = new ContentRequests.CreateTag("landscape");

    when(contentService.createTag("landscape")).thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

    verify(contentService).createTag("landscape");
  }

  // ============== POST /api/admin/content/people ==============

  @Test
  @DisplayName("POST /content/people should create a new person")
  void createPerson_shouldCreateNewPerson() throws Exception {
    // Arrange
    ContentRequests.CreatePerson request = new ContentRequests.CreatePerson("John Doe");

    Map<String, Object> response = new HashMap<>();
    response.put("person", new Records.Person(1L, "John Doe", "john-doe"));
    response.put("message", "Person created successfully");

    when(contentService.createPerson("John Doe")).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.person", notNullValue()))
        .andExpect(jsonPath("$.message", is("Person created successfully")));

    verify(contentService).createPerson("John Doe");
  }

  @Test
  @DisplayName("POST /content/people should handle duplicate person error")
  void createPerson_shouldHandleDuplicatePersonError() throws Exception {
    // Arrange
    ContentRequests.CreatePerson request = new ContentRequests.CreatePerson("John Doe");

    when(contentService.createPerson("John Doe"))
        .thenThrow(new IllegalArgumentException("Person already exists: John Doe"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Person already exists")));

    verify(contentService).createPerson("John Doe");
  }

  @Test
  @DisplayName("POST /content/people should handle general errors")
  void createPerson_shouldHandleGeneralErrors() throws Exception {
    // Arrange
    ContentRequests.CreatePerson request = new ContentRequests.CreatePerson("John Doe");

    when(contentService.createPerson("John Doe")).thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

    verify(contentService).createPerson("John Doe");
  }

  // ============== GET and DELETE endpoints ==============

  @Test
  @DisplayName("GET /content/images should return all images")
  void getAllImages_shouldReturnAllImages() throws Exception {
    // Arrange
    Pageable pageable = PageRequest.of(0, 50);
    Page<ContentModels.Image> page = new PageImpl<>(testImages, pageable, testImages.size());
    when(contentService.getAllImages(any(Pageable.class))).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/content/images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id", is(1)))
        .andExpect(jsonPath("$.content[0].title", is("Test Image")));

    verify(contentService).getAllImages(any(Pageable.class));
  }

  @Test
  @DisplayName("GET /content/images should handle errors")
  void getAllImages_shouldHandleErrors() throws Exception {
    // Arrange
    when(contentService.getAllImages(any(Pageable.class)))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc.perform(get("/api/admin/content/images")).andExpect(status().isInternalServerError());

    verify(contentService).getAllImages(any(Pageable.class));
  }

  @Test
  @DisplayName("DELETE /content/images should delete images")
  void deleteImages_shouldDeleteImages() throws Exception {
    // Arrange
    Map<String, List<Long>> request = Map.of("imageIds", List.of(1L, 2L));

    Map<String, Object> response = new HashMap<>();
    response.put("deletedIds", List.of(1L, 2L));
    response.put("message", "Images deleted successfully");

    when(contentService.deleteImages(any(List.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(
            delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedIds", hasSize(2)))
        .andExpect(jsonPath("$.message", is("Images deleted successfully")));

    verify(contentService).deleteImages(any(List.class));
  }

  @Test
  @DisplayName("DELETE /content/images should handle invalid request")
  void deleteImages_shouldHandleInvalidRequest() throws Exception {
    // Arrange
    Map<String, List<Long>> request = Map.of("imageIds", List.of(999L));

    when(contentService.deleteImages(any(List.class)))
        .thenThrow(new ResourceNotFoundException("Image not found with ID: 999"));

    // Act & Assert
    mockMvc
        .perform(
            delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("Image not found")));

    verify(contentService).deleteImages(any(List.class));
  }

  @Test
  @DisplayName("DELETE /content/images should handle general errors")
  void deleteImages_shouldHandleGeneralErrors() throws Exception {
    // Arrange
    Map<String, List<Long>> request = Map.of("imageIds", List.of(1L));

    when(contentService.deleteImages(any(List.class)))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(
            delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

    verify(contentService).deleteImages(any(List.class));
  }
}
