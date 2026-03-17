package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Response for the location page endpoint. Contains visible collections at a location and orphan
 * images (images at this location not in any of the returned collections).
 */
public record LocationPageResponse(
    Records.Location location,
    List<CollectionModel> collections,
    List<ContentModels.Image> images,
    long totalCollections,
    long totalImages) {}
