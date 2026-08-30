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

    /**
     * The case this range check exists for. Every component parses as an integer, so the old
     * implementation accepted month 13 and built an S3 path out of it. It is now unusable in the
     * same way "garbage" is, and falls through to the modify date.
     */
    @Test
    void monthOutOfRange_fallsBackToModifyDate() {
      int[] result = extractor.parseImageDate("2026:13:01 00:00:00", "2025:06:01 12:00:00");

      assertThat(result[0]).isEqualTo(2025);
      assertThat(result[1]).isEqualTo(6);
    }

    @Test
    void monthOutOfRangeOnBoth_fallsBackToCurrentDate() {
      int[] result = extractor.parseImageDate("2026:13:01 00:00:00", "2025:00:01 00:00:00");

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    /**
     * A time-first string parses cleanly into two integers that are not a year and a month. This is
     * the failure the "first two numeric runs" shortcut cannot see without a range check.
     */
    @Test
    void timeFirstString_isNotReadAsAYear() {
      int[] result = extractor.parseImageDate("14:30:00 2024:05:15", null);

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    @Test
    void yearBeforePhotographyExists_isRejected() {
      int[] result = extractor.parseImageDate("1500:06:01 00:00:00", null);

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    @Test
    void yearFarInTheFuture_isRejected() {
      int[] result = extractor.parseImageDate("9999:06:01 00:00:00", null);

      LocalDate now = LocalDate.now();
      assertThat(result[0]).isEqualTo(now.getYear());
      assertThat(result[1]).isEqualTo(now.getMonthValue());
    }

    /** Next year is allowed: a camera clock set slightly ahead should not lose its capture date. */
    @Test
    void yearOneAhead_isAccepted() {
      int nextYear = LocalDate.now().getYear() + 1;
      int[] result = extractor.parseImageDate(nextYear + ":06:01 00:00:00", null);

      assertThat(result[0]).isEqualTo(nextYear);
      assertThat(result[1]).isEqualTo(6);
    }

    @Test
    void yearOnlyWithNoMonth_fallsBackToCurrentDate() {
      int[] result = extractor.parseImageDate("2024", null);

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
