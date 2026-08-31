package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.DiskUploadRequest.FileEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-contract guard for {@link FileEntry}, written when MR 25 deleted its 3-arg convenience
 * constructor. That delete is safe only because Jackson binds a record through its canonical
 * constructor and defaults absent components to null -- the Java overload was never on the wire.
 * Nothing in the suite tested that, so the delete rested on an assumption; this is the assumption.
 */
class DiskUploadRequestWireTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("a pre-ingest from-disk body still binds, with the newer fields null")
  void shouldDeserializeABodyCarryingOnlyTheOriginalThreeFields() throws Exception {
    String json =
        "{\"files\":[{\"jpegPath\":\"/tmp/photo.jpg\",\"rawPath\":\"/tmp/photo.cr3\","
            + "\"people\":[\"Ada\"]}],\"locationIds\":null}";

    DiskUploadRequest request = mapper.readValue(json, DiskUploadRequest.class);

    assertThat(request.files()).singleElement().satisfies(this::assertPreIngestShape);
  }

  @Test
  @DisplayName("an ingest body populates all six components")
  void shouldDeserializeTheTagFirstIngestBody() throws Exception {
    String json =
        "{\"files\":[{\"jpegPath\":\"/tmp/photo.jpg\",\"rawPath\":null,\"people\":[\"Ada\"],"
            + "\"tags\":[\"landscape\"],\"locations\":[\"Seattle\"],"
            + "\"captureDate\":\"2026-08-31\"}],\"locationIds\":[42]}";

    DiskUploadRequest request = mapper.readValue(json, DiskUploadRequest.class);

    assertThat(request.locationIds()).containsExactly(42L);
    assertThat(request.files())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.tags()).containsExactly("landscape");
              assertThat(entry.locations()).containsExactly("Seattle");
              assertThat(entry.captureDate()).isEqualTo("2026-08-31");
            });
  }

  private void assertPreIngestShape(FileEntry entry) {
    assertThat(entry.jpegPath()).isEqualTo("/tmp/photo.jpg");
    assertThat(entry.rawPath()).isEqualTo("/tmp/photo.cr3");
    assertThat(entry.people()).containsExactly("Ada");
    assertThat(entry.tags()).isNull();
    assertThat(entry.locations()).isNull();
    assertThat(entry.captureDate()).isNull();
  }
}
