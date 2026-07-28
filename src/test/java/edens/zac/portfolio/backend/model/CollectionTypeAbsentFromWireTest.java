package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * The legacy type field must not appear on any collection request or response payload — and a
 * still-deployed pre-U4 client that keeps SENDING it must not break.
 *
 * <p>Those are two different claims and they need two different mappers. The structural assertions
 * use a bespoke {@link ObjectMapper}; the back-compat ones use {@link Jackson2ObjectMapperBuilder},
 * which is what Spring Boot's {@code JacksonAutoConfiguration} hands to {@code @RequestBody}
 * binding — critically, with {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled. A bespoke {@code new
 * ObjectMapper()} rejects an unknown {@code type} key and would "prove" a rejection the app does
 * not actually perform.
 */
class CollectionTypeAbsentFromWireTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  /** The mapper Spring Boot auto-configures for request-body binding. */
  private final ObjectMapper bootMapper = Jackson2ObjectMapperBuilder.json().build();

  @Test
  @DisplayName("CollectionModel serializes without a type property")
  void collectionModel_hasNoTypeProperty() throws Exception {
    CollectionModel model = CollectionModel.builder().id(1L).slug("s").title("Title").build();

    String json = objectMapper.writeValueAsString(model);

    assertThat(json).doesNotContain("\"type\"");
  }

  @Test
  @DisplayName("a Create request has no type component")
  void createRequest_hasNoTypeComponent() {
    assertThat(CollectionRequests.Create.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("type");
  }

  @Test
  @DisplayName("an Update request has no type component")
  void updateRequest_hasNoTypeComponent() {
    assertThat(CollectionRequests.Update.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("type");
  }

  @Test
  @DisplayName("SaveAsCollectionRequest has no type component")
  void saveAsCollectionRequest_hasNoTypeComponent() {
    assertThat(SaveAsCollectionRequest.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("type");
  }

  // ---------------------------------------------------------------------------
  //  Back-compat: a stale client's `type` key is tolerated, not rejected
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Boot's request-body mapper does not fail on unknown properties")
  void bootMapper_toleratesUnknownProperties() {
    // The premise the three tests below rest on, asserted directly so that flipping
    // spring.jackson.deserialization.fail-on-unknown-properties=true fails HERE, with a name that
    // says why, instead of only breaking every pre-U4 client in production.
    assertThat(bootMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
  }

  @Test
  @DisplayName("a Create body still carrying type binds, leaving the flags null")
  void createRequest_legacyTypePayloadStillBinds() throws Exception {
    String json =
        """
        {"type": "BLOG", "title": "Legacy Blog"}
        """;

    CollectionRequests.Create create = bootMapper.readValue(json, CollectionRequests.Create.class);

    assertThat(create.title()).isEqualTo("Legacy Blog");
    assertThat(create.isClient()).isNull();
    assertThat(create.isBlog()).isNull();
  }

  @Test
  @DisplayName("an Update body still carrying type binds, leaving the flags null")
  void updateRequest_legacyTypePayloadStillBinds() throws Exception {
    String json =
        """
        {"id": 9, "type": "PORTFOLIO", "title": "Legacy Portfolio"}
        """;

    CollectionRequests.Update update = bootMapper.readValue(json, CollectionRequests.Update.class);

    assertThat(update.id()).isEqualTo(9L);
    assertThat(update.title()).isEqualTo("Legacy Portfolio");
    assertThat(update.isClient()).isNull();
    assertThat(update.isBlog()).isNull();
  }

  @Test
  @DisplayName("a SaveAsCollectionRequest body still carrying type binds, leaving the flags null")
  void saveAsCollectionRequest_legacyTypePayloadStillBinds() throws Exception {
    String json =
        """
        {"type": "PORTFOLIO"}
        """;

    SaveAsCollectionRequest request = bootMapper.readValue(json, SaveAsCollectionRequest.class);

    assertThat(request.isClient()).isNull();
    assertThat(request.isBlog()).isNull();
  }
}
