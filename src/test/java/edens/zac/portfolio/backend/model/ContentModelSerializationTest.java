package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.types.ContentType;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentModelSerializationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Test
  @DisplayName("ContentModels.Image should serialize to JSON correctly")
  void imageModel_shouldSerializeToJson() throws IOException {
    LocalDateTime now = LocalDateTime.of(2023, 1, 1, 12, 0, 0);

    ContentModels.Image model =
        new ContentModels.Image(
            1L,
            ContentType.IMAGE,
            "Test caption",
            null,
            "https://example.com/img.jpg",
            null,
            3,
            true,
            now,
            now,
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

    String json = objectMapper.writeValueAsString(model);

    assertTrue(json.contains("\"id\":1"));
    assertTrue(json.contains("\"orderIndex\":3"));
    assertTrue(json.contains("\"contentType\":\"IMAGE\""));
    assertTrue(json.contains("\"title\":\"Test caption\""));
    assertTrue(json.contains("\"createdAt\":[2023,1,1,12,0]"));
    assertTrue(json.contains("\"updatedAt\":[2023,1,1,12,0]"));
  }

  @Test
  @DisplayName("ContentModels.Image should deserialize from JSON correctly")
  void imageModel_shouldDeserializeFromJson() throws IOException {
    String jsonContent =
        """
            {
                "contentType": "IMAGE",
                "id": 1,
                "orderIndex": 3,
                "title": "Test caption",
                "createdAt": "2023-01-01T12:00:00",
                "updatedAt": "2023-01-01T12:00:00"
            }
            """;

    ContentModels.Image result = objectMapper.readValue(jsonContent, ContentModels.Image.class);

    assertEquals(1L, result.id());
    assertEquals(3, result.orderIndex());
    assertEquals(ContentType.IMAGE, result.contentType());
    assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result.createdAt());
    assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result.updatedAt());
  }

  @Test
  @DisplayName("ContentModel sealed subtypes should serialize and deserialize with polymorphism")
  void contentModel_shouldHandlePolymorphicSerialization() throws IOException {
    LocalDateTime now = LocalDateTime.of(2023, 1, 1, 12, 0, 0);

    ContentModels.Image imageModel =
        new ContentModels.Image(
            1L,
            ContentType.IMAGE,
            null,
            null,
            "https://example.com/image.jpg",
            null,
            0,
            true,
            now,
            now,
            1200,
            800,
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

    ContentModels.Text textModel =
        new ContentModels.Text(
            2L,
            ContentType.TEXT,
            null,
            null,
            null,
            1,
            true,
            now,
            now,
            "This is some text content",
            "markdown");

    List<ContentModel> content = List.of(imageModel, textModel);

    String json = objectMapper.writeValueAsString(content);

    List<ContentModel> deserializedContent =
        objectMapper.readValue(
            json,
            objectMapper.getTypeFactory().constructCollectionType(List.class, ContentModel.class));

    assertEquals(2, deserializedContent.size());

    assertInstanceOf(ContentModels.Image.class, deserializedContent.getFirst());
    ContentModels.Image deserializedImage = (ContentModels.Image) deserializedContent.getFirst();
    assertEquals(1L, deserializedImage.id());
    assertEquals("https://example.com/image.jpg", deserializedImage.imageUrl());
    assertEquals(1200, deserializedImage.imageWidth());
    assertEquals(800, deserializedImage.imageHeight());

    assertInstanceOf(ContentModels.Text.class, deserializedContent.get(1));
    ContentModels.Text deserializedText = (ContentModels.Text) deserializedContent.get(1);
    assertEquals(2L, deserializedText.id());
    assertEquals("This is some text content", deserializedText.textContent());
  }
}
