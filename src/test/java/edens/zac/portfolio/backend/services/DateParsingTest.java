package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DateParsingTest {

  private final ImageMetadataExtractor extractor = new ImageMetadataExtractor();

  @Nested
  class ParseImageDate {

    @Test
    void validCreateDate_returnsYearAndMonthFromCreateDate() {
      int[] result = extractor.parseImageDate("2026:01:26 17:48:38", null);

      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void nullCreateDate_validModifyDate_fallsBackToModifyDate() {
      int[] result = extractor.parseImageDate(null, "2025:08:15 10:30:00");

      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(2025);
      assertThat(result[1]).isEqualTo(8);
    }

    @Test
    void bothNull_fallsBackToCurrentDate() {
      int[] result = extractor.parseImageDate(null, null);

      LocalDate now = LocalDate.now();
      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    @Test
    void exifDateFormat_parsesCorrectly() {
      int[] result = extractor.parseImageDate("2026:01:26 17:48:38", null);

      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void isoDateFormat_parsesCorrectly() {
      int[] result = extractor.parseImageDate("2026-01-26T17:48:38", null);

      assertThat(result[0]).isEqualTo(2026);
      assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void malformedCreateDate_fallsBackToModifyDate() {
      int[] result = extractor.parseImageDate("not-a-date", "2025:06:01 12:00:00");

      assertThat(result[0]).isEqualTo(2025);
      assertThat(result[1]).isEqualTo(6);
    }

    @Test
    void bothMalformed_fallsBackToCurrentDate() {
      int[] result = extractor.parseImageDate("garbage", "also-garbage");

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }
  }

  @Nested
  class ParseExifDateToLocalDateTime {

    @Test
    void exifFormat_parsesToLocalDateTime() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("2026:01:26 17:48:38");

      assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 26, 17, 48, 38));
    }

    @Test
    void isoFormat_parsesCorrectly() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("2026-01-26T17:48:38");

      assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 26, 17, 48, 38));
    }

    @Test
    void isoFormatWithTimezoneOffset_parsesCorrectly() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("2020-09-27T08:42:51-07:00");

      assertThat(result).isEqualTo(LocalDateTime.of(2020, 9, 27, 8, 42, 51));
    }

    @Test
    void isoDateOnly_returnsMidnight() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("2020-09-27");

      assertThat(result).isEqualTo(LocalDateTime.of(2020, 9, 27, 0, 0, 0));
    }

    @Test
    void nullInput_returnsNull() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime(null);

      assertThat(result).isNull();
    }

    @Test
    void emptyInput_returnsNull() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("  ");

      assertThat(result).isNull();
    }

    @Test
    void malformedInput_returnsNull() {
      LocalDateTime result = extractor.parseExifDateToLocalDateTime("not-a-valid-date");

      assertThat(result).isNull();
    }
  }
}
