package edens.zac.portfolio.backend.controller.admin;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The grantable subset of AccessLevel: ADMIN is a computed sentinel (users.is_admin is the sole
 * source of admin truth) and must never be stored as a grant. Field-level so the rejection surfaces
 * as a FIELD error -- GlobalExceptionHandler joins only field errors into 400 messages. The V55
 * CHECK constraint is the database backstop behind this rejection.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GrantableLevelValidator.class)
public @interface GrantableLevel {
  /** Validation failure message. */
  String message() default "ADMIN is not grantable; admin is global and lives on users.is_admin";

  /** Validation groups, per the Bean Validation spec. */
  Class<?>[] groups() default {};

  /** Payload, per the Bean Validation spec. */
  Class<? extends Payload>[] payload() default {};
}
