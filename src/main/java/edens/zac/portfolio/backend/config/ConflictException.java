package edens.zac.portfolio.backend.config;

/**
 * Thrown when a write clashes with an existing record. Maps to HTTP 409 with the message passed to
 * the client verbatim, so the message must say which record clashes and what to do instead.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
