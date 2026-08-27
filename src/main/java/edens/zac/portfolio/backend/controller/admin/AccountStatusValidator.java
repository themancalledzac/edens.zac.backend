package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Refuses PERSON; null is left to @NotNull. See {@link AccountStatus}. */
public class AccountStatusValidator implements ConstraintValidator<AccountStatus, UserStatus> {

  @Override
  public boolean isValid(UserStatus value, ConstraintValidatorContext context) {
    return value != UserStatus.PERSON;
  }
}
