package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.types.DisplayMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollaboratorRequestsTest {

  @Test
  void toUpdateCarriesEveryPermittedFieldAndLeavesEveryDeniedFieldNull() {
    var locations = new CollectionRequests.LocationUpdate(List.of(1L), List.of("Rainier"), null);
    var tags = new CollectionRequests.TagUpdate(List.of(2L), List.of("alpine"), null);
    var narrow =
        new CollaboratorRequests.CollaboratorUpdate(
            5L,
            "New Title",
            "New description",
            LocalDate.of(2026, 8, 1),
            true,
            locations,
            tags,
            77L,
            4,
            DisplayMode.CHRONOLOGICAL,
            3,
            30);

    CollectionRequests.Update wide = narrow.toUpdate();

    // Permitted fields carried through.
    assertThat(wide.id()).isEqualTo(5L);
    assertThat(wide.title()).isEqualTo("New Title");
    assertThat(wide.description()).isEqualTo("New description");
    assertThat(wide.collectionDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(wide.clearCollectionDate()).isTrue();
    assertThat(wide.locations()).isSameAs(locations);
    assertThat(wide.tags()).isSameAs(tags);
    assertThat(wide.coverImageId()).isEqualTo(77L);
    assertThat(wide.rating()).isEqualTo(4);
    assertThat(wide.displayMode()).isEqualTo(DisplayMode.CHRONOLOGICAL);
    assertThat(wide.rowsWide()).isEqualTo(3);
    assertThat(wide.contentPerPage()).isEqualTo(30);

    // Denied fields pinned null -- the compiler-enforced boundary of spec section 7.
    assertThat(wide.slug()).isNull();
    assertThat(wide.visibility()).isNull();
    assertThat(wide.collectionEndDate()).isNull();
    assertThat(wide.clearCollectionEndDate()).isNull();
    assertThat(wide.isClient()).isNull();
    assertThat(wide.isBlog()).isNull();
    assertThat(wide.people()).isNull();
    assertThat(wide.collections()).isNull();
    assertThat(wide.siblings()).isNull();
    assertThat(wide.parents()).isNull();
  }
}
