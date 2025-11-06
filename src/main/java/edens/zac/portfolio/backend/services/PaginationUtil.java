package edens.zac.portfolio.backend.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static edens.zac.portfolio.backend.config.DefaultValues.default_collection_per_page;
import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

/**
 * Utility class for pagination normalization and validation.
 * Provides consistent pagination handling across controllers and services.
 */
public class PaginationUtil {

    /**
     * Normalize pagination parameters for collection endpoints.
     * Ensures page is non-negative (0-based) and size is positive.
     *
     * @param page Page number (0-based, can be negative)
     * @param size Page size (can be 0 or negative)
     * @return Normalized Pageable with valid page and size
     */
    public static Pageable normalizeCollectionPageable(int page, int size) {
        // Normalize page: 0-based; negative coerced to 0
        int normalizedPage = Math.max(0, page);
        
        // Normalize size: must be positive; use default if invalid
        int normalizedSize = size <= 0 ? default_collection_per_page : size;
        
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    /**
     * Normalize pagination parameters for content endpoints.
     * Ensures page is non-negative (0-based) and size is positive.
     *
     * @param page Page number (0-based, can be negative)
     * @param size Page size (can be 0 or negative)
     * @return Normalized Pageable with valid page and size
     */
    public static Pageable normalizeContentPageable(int page, int size) {
        // Normalize page: 0-based; negative coerced to 0
        int normalizedPage = Math.max(0, page);
        
        // Normalize size: must be positive; use default if invalid
        int normalizedSize = size <= 0 ? default_content_per_page : size;
        
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    /**
     * Normalize page number (0-based, negative coerced to 0).
     *
     * @param page Page number
     * @return Normalized page number (>= 0)
     */
    public static int normalizePage(int page) {
        return Math.max(0, page);
    }

    /**
     * Normalize size with default value.
     *
     * @param size        Page size
     * @param defaultSize Default size to use if size is invalid
     * @return Normalized size (>= 1)
     */
    public static int normalizeSize(int size, int defaultSize) {
        return size <= 0 ? defaultSize : size;
    }
}