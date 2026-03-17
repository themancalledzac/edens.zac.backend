package edens.zac.portfolio.backend.model;

import java.util.List;

/** Paginated response for image search results. */
public record ImageSearchResponse(
    List<ContentModels.Image> content, long totalElements, int totalPages) {}
