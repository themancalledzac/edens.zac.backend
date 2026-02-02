package edens.zac.portfolio.backend.services.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator for metadata creation operations (tags, people, cameras, film types). Centralizes
 * validation logic for metadata operations.
 */
@Component
@Slf4j
public class MetadataValidator {

  /**
   * Validate a tag name.
   *
   * @param tagName The tag name to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateTagName(String tagName) {
    if (tagName == null || tagName.trim().isEmpty()) {
      throw new IllegalArgumentException("tagName is required");
    }
  }

  /**
   * Validate a person name.
   *
   * @param personName The person name to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validatePersonName(String personName) {
    if (personName == null || personName.trim().isEmpty()) {
      throw new IllegalArgumentException("personName is required");
    }
  }

  /**
   * Validate film type creation parameters.
   *
   * @param filmTypeName The technical film type name (e.g., "KODAK_PORTRA_400")
   * @param displayName The display name (e.g., "Kodak Portra 400")
   * @param defaultIso The default ISO value
   * @throws IllegalArgumentException if validation fails
   */
  public void validateFilmType(String filmTypeName, String displayName, Integer defaultIso) {
    if (filmTypeName == null || filmTypeName.trim().isEmpty()) {
      throw new IllegalArgumentException("filmTypeName is required");
    }
    if (displayName == null || displayName.trim().isEmpty()) {
      throw new IllegalArgumentException("displayName is required");
    }
    if (defaultIso == null || defaultIso <= 0) {
      throw new IllegalArgumentException("defaultIso must be a positive integer");
    }
  }
}
