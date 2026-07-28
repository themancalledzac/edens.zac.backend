package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The legacy type field must not appear on any collection request or response payload. */
class CollectionTypeAbsentFromWireTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  @DisplayName("CollectionModel serializes without a type property")
  void collectionModel_hasNoTypeProperty() throws Exception {
    CollectionModel model = CollectionModel.builder().id(1L).slug("s").title("Title").build();

    String json = objectMapper.writeValueAsString(model);

    assertThat(json).doesNotContain("\"type\"");
  }

  @Test
  @DisplayName("a Create request body carrying type is rejected as an unknown property")
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
}
