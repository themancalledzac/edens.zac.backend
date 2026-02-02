package edens.zac.portfolio.backend.config;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler that eliminates duplicate try-catch blocks across all controllers.
 * Provides consistent error responses and logging for all exception types.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * Standard error response record for consistent API error responses.
   *
   * @param timestamp When the error occurred
   * @param status HTTP status code
   * @param error HTTP status reason phrase
   * @param message Detailed error message
   */
  public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message) {

    public static ErrorResponse of(HttpStatus status, String message) {
      return new ErrorResponse(
          LocalDateTime.now(), status.value(), status.getReasonPhrase(), message);
    }
  }

  /**
   * Handle IllegalArgumentException - typically used for "not found" or invalid input scenarios.
   * Returns 404 if message contains "not found", otherwise 400.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
    String message = e.getMessage();

    if (message != null && message.toLowerCase().contains("not found")) {
      log.warn("Resource not found: {}", message);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ErrorResponse.of(HttpStatus.NOT_FOUND, message));
    }

    log.warn("Bad request - invalid argument: {}", message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  /** Handle IllegalStateException - typically indicates a bad request or invalid state. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
    log.warn("Bad request - illegal state: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
  }

  /** Handle validation errors from @Valid annotations on request bodies. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));

    log.warn("Validation failed: {}", message);
    return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  /** Handle constraint violations from @Validated on path/query parameters. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    String message =
        e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));

    log.warn("Constraint violation: {}", message);
    return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  /** Handle type mismatch errors (e.g., passing a string where a Long is expected). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    String requiredType =
        e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
    String message = String.format("Parameter '%s' must be of type %s", e.getName(), requiredType);

    log.warn("Type mismatch: {}", message);
    return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  /** Handle data integrity violations (e.g., unique constraint violations). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
    log.error("Data integrity violation: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ErrorResponse.of(
                HttpStatus.CONFLICT, "Data integrity violation: duplicate or invalid data"));
  }

  /** Catch-all handler for unexpected exceptions. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
    log.error("Unhandled exception", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
  }
}
