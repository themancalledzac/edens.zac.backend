package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Response for the location page endpoint: the visible collections at a location, plus the orphan
 * content tagged with that location and not held by any of those collections.
 *
 * <p>{@code images} keeps its name but carries mixed content -- images and GIFs, the two types
 * {@code content_image_locations} can tag and a location page renders. A GIF appears only if one is
 * actually tagged, so a location with none serializes exactly as it did before.
 */
public record LocationPageResponse(
    Records.Location location,
    List<CollectionModel> collections,
    List<ContentModel> images,
    long totalCollections,
    long totalImages) {}
