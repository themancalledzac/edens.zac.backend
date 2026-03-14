package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentCameraDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentFilmTypeDao;
import edens.zac.portfolio.backend.dao.ContentGifDao;
import edens.zac.portfolio.backend.dao.ContentLensDao;
import edens.zac.portfolio.backend.dao.ContentPersonDao;
import edens.zac.portfolio.backend.dao.ContentTagDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.dao.LocationDao;
import edens.zac.portfolio.backend.dao.TagDao;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class DateParsingTest {

  @Mock private S3Client s3Client;
  @Mock private ContentDao contentDao;
  @Mock private CollectionDao collectionDao;
  @Mock private ContentCameraDao contentCameraDao;
  @Mock private ContentLensDao contentLensDao;
  @Mock private ContentFilmTypeDao contentFilmTypeDao;
  @Mock private ContentTagDao contentTagDao;
  @Mock private TagDao tagDao;
  @Mock private ContentPersonDao contentPersonDao;
  @Mock private LocationDao locationDao;
  @Mock private CollectionContentDao collectionContentDao;
  @Mock private ContentTextDao contentTextDao;
  @Mock private ContentCollectionDao contentCollectionDao;
  @Mock private ContentGifDao contentGifDao;
  @Mock private ContentImageUpdateValidator contentImageUpdateValidator;
  @Mock private ContentValidator contentValidator;

  @InjectMocks private ContentProcessingUtil util;

  @Nested
  class ParseImageDate {

    @Test
    void validCreateDate_returnsYearAndMonthFromCreateDate() {
      int[] result = util.parseImageDate("2026:01:26 17:48:38", null);

      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void nullCreateDate_validModifyDate_fallsBackToModifyDate() {
      int[] result = util.parseImageDate(null, "2025:08:15 10:30:00");

      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(2025);
      assertThat(result[1]).isEqualTo(8);
    }

    @Test
    void bothNull_fallsBackToCurrentDate() {
      int[] result = util.parseImageDate(null, null);

      LocalDate now = LocalDate.now();
      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    @Test
    void exifDateFormat_parsesCorrectly() {
      int[] result = util.parseImageDate("2026:01:26 17:48:38", null);

      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void isoDateFormat_parsesCorrectly() {
      int[] result = util.parseImageDate("2026-01-26T17:48:38", null);

      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void malformedCreateDate_fallsBackToModifyDate() {
      int[] result = util.parseImageDate("not-a-date", "2025:06:01 12:00:00");

      assertThat(result[0]).isEqualTo(2025);
      assertThat(result[1]).isEqualTo(6);
    }

    @Test
    void bothMalformed_fallsBackToCurrentDate() {
      int[] result = util.parseImageDate("garbage", "also-garbage");

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }
  }

  @Nested
  class ParseExifDateToLocalDateTime {

    @Test
    void exifFormat_parsesToLocalDateTime() {
      LocalDateTime result = util.parseExifDateToLocalDateTime("2026:01:26 17:48:38");

      assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 26, 17, 48, 38));
    }

    @Test
    void isoFormat_returnsNullBecauseMethodOnlyHandlesExifFormat() {
      // parseExifDateToLocalDateTime replaces the first two colons with dashes,
      // which mangles ISO-8601 dates (time colons get replaced instead of date colons).
      // This is expected -- the method is designed for EXIF format only.
      LocalDateTime result = util.parseExifDateToLocalDateTime("2026-01-26T17:48:38");

      assertThat(result).isNull();
    }

    @Test
    void nullInput_returnsNull() {
      LocalDateTime result = util.parseExifDateToLocalDateTime(null);

      assertThat(result).isNull();
    }

    @Test
    void emptyInput_returnsNull() {
      LocalDateTime result = util.parseExifDateToLocalDateTime("  ");

      assertThat(result).isNull();
    }

    @Test
    void malformedInput_returnsNull() {
      LocalDateTime result = util.parseExifDateToLocalDateTime("not-a-valid-date");

      assertThat(result).isNull();
    }
  }
}
