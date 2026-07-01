package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;

/**
 * Request body for promoting a tag-view into a real collection. Both fields are optional; the
 * service defaults type to PORTFOLIO and visibility to UNLISTED.
 */
public record SaveAsCollectionRequest(CollectionType type, CollectionVisibility visibility) {}
