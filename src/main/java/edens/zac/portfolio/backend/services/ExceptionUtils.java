package edens.zac.portfolio.backend.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Utility class for handling exceptions in a consistent way across the application.
 * Provides methods for standardized exception handling and logging.
 */
@Component
@Slf4j
public class ExceptionUtils {

    /**
     * Generic error handler for operations.
     * 
     * @param operation Description of the operation being performed
     * @param action Supplier function that performs the operation
     * @return Result of the operation
     * @param <T> Type of the result
     */
    public <T> T handleExceptions(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (EntityNotFoundException e) {
            log.warn("Entity not found during {}: {}", operation, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during {}: {}", operation, e.getMessage(), e);
            throw new RuntimeException("Failed to " + operation + ": " + e.getMessage(), e);
        }
    }
}