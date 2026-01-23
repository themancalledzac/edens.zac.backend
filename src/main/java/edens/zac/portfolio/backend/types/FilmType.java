package edens.zac.portfolio.backend.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Enum representing different film stock types with their default ISO values. Used for categorizing
 * film photography images.
 */
@Slf4j
public enum FilmType {
  KODAK_PORTRA_400("Kodak Portra 400", 400),
  KODAK_PORTRA_800("Kodak Portra 800", 800),
  KODAK_PORTRA_160("Kodak Portra 160", 160),
  KODAK_GOLD_200("Kodak Gold 200", 200),
  KODAK_EKTAR_100("Kodak Ektar 100", 100),
  CINESTILL_800T("Cinestill 800T", 800),
  CINESTILL_400("Cinestill 400", 400),
  CINESTILL_50("Cinestill 50", 50),
  ROLLEI_RPX_100("Rollei RPX 100", 100),
  ILFORD_FP4_PLUS_125("Ilford FP4 Plus 125", 125),
  KODAK_TRI_X_400TX("Kodak Tri-X 400TX", 400),
  CANDIDO_800("Candido 800", 800),
  BERGGER_PANCRO_400("Bergger Pancro 400", 400),
  FUJI_VELVIA_100F("Fuji Velvia 100F", 100),
  KODAK_EKTACHROME_100PLUS("Kodak Ektachrome 100Plus", 100),
  KODAK_EKTACHROME_100VS("Kodak Ektachrome 100VS", 100),
  ILFORD_PANF_PLUS_50("Ilford PanF Plus 50", 50),
  ILFORD_XP2_SUPER_400("Ilford XP2 Super 400", 400);

  @NotNull @Getter private final String displayName;

  @NotNull @Getter private final Integer defaultIso;

  FilmType(String displayName, Integer defaultIso) {
    this.displayName = displayName;
    this.defaultIso = defaultIso;
  }

  /**
   * Returns the enum name for JSON serialization
   *
   * @return enum name (e.g., "KODAK_PORTRA_400")
   */
  @JsonValue
  public String getValue() {
    return this.name();
  }

  /**
   * Creates FilmType from string value for JSON deserialization
   *
   * @param value the string value
   * @return FilmType enum or null if invalid
   */
  @JsonCreator
  public static FilmType forValue(String value) {
    if (value == null) {
      log.warn("Null FilmType value provided");
      return null;
    }

    try {
      return FilmType.valueOf(value);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid FilmType value: {}", value);
      return null;
    }
  }
}
