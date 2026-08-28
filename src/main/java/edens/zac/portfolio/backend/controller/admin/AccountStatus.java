package edens.zac.portfolio.backend.controller.admin;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The account subset of UserStatus: INVITED / ACTIVE / DISABLED. PERSON is a tag-only identity
 * rather than an account lifecycle state, and admin cannot write it -- the only supported way into
 * PERSON is the V35 identity import, and the only supported way out is upgrade or merge.
 *
 * <p>Field-level so the rejection surfaces as a FIELD error -- GlobalExceptionHandler joins only
 * field errors into 400 messages.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AccountStatusValidator.class)
public @interface AccountStatus {
  /** Validation failure message. */
  String message() default
      "PERSON is not an account status; use the upgrade or merge endpoint to change a person";

  /** Validation groups, per the Bean Validation spec. */
  Class<?>[] groups() default {};

  /** Payload, per the Bean Validation spec. */
  Class<? extends Payload>[] payload() default {};
}
