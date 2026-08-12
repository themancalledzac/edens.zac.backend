package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.types.AccessLevel;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Refuses the ADMIN sentinel; null is left to @NotNull. See {@link GrantableLevel}. */
public class GrantableLevelValidator implements ConstraintValidator<GrantableLevel, AccessLevel> {

  @Override
  public boolean isValid(AccessLevel value, ConstraintValidatorContext context) {
    return value != AccessLevel.ADMIN;
  }
}
