package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks the exact JSON property names of the new client/blog booleans on every collection-bearing
 * payload. Guards against the Lombok/Jackson boolean-getter trap where a boolean {@code isClient}
 * getter {@code isClient()} silently serializes as {@code "client"}.
 */
class CollectionFlagSerializationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Test
  @DisplayName("CollectionModel serializes isClient/isBlog under exactly those names")
  void collectionModel_serializesExactFlagNames() throws Exception {
    CollectionModel model =
        CollectionModel.builder()
            .id(1L)
            .type(CollectionType.CLIENT_GALLERY)
            .isClient(true)
            .isBlog(false)
            .title("Smith Wedding")
            .slug("smith-wedding")
            .build();

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(model));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isTrue();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isFalse();
    // The boolean-getter rename trap would emit these instead:
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
    // Legacy type still emitted during the dual-compat window.
    assertThat(json.get("type").asText()).isEqualTo("CLIENT_GALLERY");
  }

  @Test
  @DisplayName("Records.CollectionList serializes isClient/isBlog under exactly those names")
  void collectionList_serializesExactFlagNames() throws Exception {
    Records.CollectionList list =
        new Records.CollectionList(
            2L, "Daily Blog", "daily-blog", CollectionType.BLOG, null, null, false, true);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(list));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isFalse();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isTrue();
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
  }

  @Test
  @DisplayName("ContentModels.Collection serializes isClient/isBlog under exactly those names")
  void contentModelsCollection_serializesExactFlagNames() throws Exception {
    ContentModels.Collection block =
        new ContentModels.Collection(
            3L,
            edens.zac.portfolio.backend.types.ContentType.COLLECTION,
            "Wrapped Gallery",
            null,
            null,
            0,
            true,
            null,
            null,
            30L,
            "wrapped-gallery",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            null,
            null,
            null,
            List.of());

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(block));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isTrue();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isFalse();
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
  }

  @Test
  @DisplayName("Create request deserializes isClient/isBlog from exactly those names")
  void createRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"title": "New Gallery", "isClient": true, "isBlog": false}
        """;

    CollectionRequests.Create create =
        objectMapper.readValue(json, CollectionRequests.Create.class);

    assertThat(create.isClient()).isTrue();
    assertThat(create.isBlog()).isFalse();
    assertThat(create.type()).isNull();
    assertThat(create.title()).isEqualTo("New Gallery");
  }

  @Test
  @DisplayName("Create request without booleans leaves them null (legacy type-only payload)")
  void createRequest_legacyPayloadLeavesFlagsNull() throws Exception {
    String json =
        """
        {"type": "BLOG", "title": "Legacy Blog"}
        """;

    CollectionRequests.Create create =
        objectMapper.readValue(json, CollectionRequests.Create.class);

    assertThat(create.type()).isEqualTo(CollectionType.BLOG);
    assertThat(create.isClient()).isNull();
    assertThat(create.isBlog()).isNull();
  }

  @Test
  @DisplayName("SaveAsCollectionRequest deserializes isClient/isBlog from exactly those names")
  void saveAsCollectionRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"isClient": true, "isBlog": false}
        """;

    SaveAsCollectionRequest request = objectMapper.readValue(json, SaveAsCollectionRequest.class);

    assertThat(request.isClient()).isTrue();
    assertThat(request.isBlog()).isFalse();
    assertThat(request.type()).isNull();
  }

  @Test
  @DisplayName("SaveAsCollectionRequest without booleans leaves them null (legacy payload)")
  void saveAsCollectionRequest_legacyPayloadLeavesFlagsNull() throws Exception {
    String json =
        """
        {"type": "PORTFOLIO"}
        """;

    SaveAsCollectionRequest request = objectMapper.readValue(json, SaveAsCollectionRequest.class);

    assertThat(request.type()).isEqualTo(CollectionType.PORTFOLIO);
    assertThat(request.isClient()).isNull();
    assertThat(request.isBlog()).isNull();
  }

  @Test
  @DisplayName("Update request deserializes isClient/isBlog from exactly those names")
  void updateRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"id": 9, "isClient": false, "isBlog": true}
        """;

    CollectionRequests.Update update =
        objectMapper.readValue(json, CollectionRequests.Update.class);

    assertThat(update.id()).isEqualTo(9L);
    assertThat(update.isClient()).isFalse();
    assertThat(update.isBlog()).isTrue();
    assertThat(update.type()).isNull();
  }
}
