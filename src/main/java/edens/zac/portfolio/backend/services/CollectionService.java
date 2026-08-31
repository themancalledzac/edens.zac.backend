package edens.zac.portfolio.backend.services;

import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

import edens.zac.portfolio.backend.config.CurrentUser;
import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollaboratorRequests;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessRequest;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessResponse;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing Collection entities with pagination and client gallery access.
 *
 * <p>Creating or deleting a collection changes the cached collection list, so every such path calls
 * {@link ReadCacheInvalidator#markChanged()} to drop the CDN copy on commit.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionService {

  private final CollectionRepository collectionRepository;
  private final CollectionPeopleRepository collectionPeopleRepository;
  private final CollectionSiblingRepository collectionSiblingRepository;
  private final ContentRepository contentRepository;
  private final LocationRepository locationRepository;
  private final TagRepository tagRepository;
  private final ContentMutationUtil contentMutationUtil;
  private final ContentModelConverter contentModelConverter;
  private final ContentService contentService;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final MetadataService metadataService;
  private final EmailService emailService;
  private final SyntheticCollectionResolver syntheticResolver;
  private final TagViewResolver tagViewResolver;
  private final ClientGalleryAuthService clientGalleryAuthService;
  private final CollectionAccessService collectionAccessService;
  private final RoleGrantPropagationService roleGrantPropagationService;
  private final Environment springEnv;
  private final CacheManager cacheManager;
  private final ReadCacheInvalidator readCacheInvalidator;

  // Self-reference through the Spring proxy. Required so internal calls to the @Cacheable
  // getGeneralMetadata() are intercepted by the caching aspect; a direct this.getGeneralMetadata()
  // is self-invoked and silently bypasses the cache. ObjectProvider is resolved lazily, so this
  // does not create a circular-dependency failure at startup.
  private final ObjectProvider<CollectionService> selfProvider;

  private static final int DEFAULT_PAGE_SIZE = default_content_per_page;
  private static final String HOME_SLUG = "home";

  /**
   * Read one page of a collection by slug.
   *
   * <p>Resolution order is synthetic list slug -> real collection -> tag-view -> 404. Synthetic
   * slugs ({@code all-collections}, {@code all-blogs}) bypass the DB lookup entirely and resolve
   * into a PARENT-shaped model populated with children. The real-collection lookup runs before the
   * tag-view fallback, so a real collection always wins a slug collision; otherwise any tag is
   * browsable at {@code /{slug}}, and a tag with zero visible members falls through to 404.
   *
   * <p>Every collection paginates identically -- there is no children-only read shape any more
   * (spec D1). Rows written before V51 have {@code content_per_page} backfilled by that migration.
   *
   * <p>Siblings are populated LISTED-only: this is the public read path, and unlisted siblings
   * would leak as dead links.
   */
  @Transactional(readOnly = true)
  public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
    log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

    if (syntheticResolver.isSyntheticSlug(slug)) {
      return syntheticResolver.resolve(slug, isLocalEnvironment());
    }

    Optional<CollectionEntity> collectionOpt = collectionRepository.findBySlug(slug);
    if (collectionOpt.isEmpty()) {
      Optional<CollectionModel> tagView =
          tagViewResolver.resolveTagView(slug, isLocalEnvironment());
      if (tagView.isPresent()) {
        return tagView.get();
      }
      throw new ResourceNotFoundException("Collection not found with slug: " + slug);
    }
    CollectionEntity collection = collectionOpt.get();

    enforceVisibility(collection, slug, isLocalEnvironment());

    int normalizedPage = Math.max(0, page);
    int normalizedSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    int offset = normalizedPage * normalizedSize;

    List<CollectionContentEntity> collectionContentList;
    long totalElements;

    totalElements = collectionRepository.countContentByCollectionId(collection.getId());
    collectionContentList =
        collectionRepository.findContentByCollectionId(collection.getId(), normalizedSize, offset);

    CollectionModel model =
        collectionProcessingUtil.convertToModel(
            collection, collectionContentList, normalizedPage, normalizedSize, totalElements);

    collectionProcessingUtil.populateCollectionsOnContent(model);

    collectionProcessingUtil.populateSiblings(model, true);

    filterNonListedChildCollections(model);

    return model;
  }

  /**
   * Find LISTED blog collections ({@code is_blog = true}), ordered by rating then collection_date.
   * Used by AdminHomeService to pick cover images for the blogs admin home tile.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findVisibleBlogsOrderedByDate() {
    log.debug("Finding visible blog collections ordered by date");
    List<CollectionEntity> collections = collectionRepository.findListedBlogsOrdered();
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  /**
   * Find non-HIDDEN client galleries ({@code is_client = true}, LISTED + UNLISTED) for admin-only
   * contexts where UNLISTED is acceptable to surface. Used by {@link AdminHomeService} to pick
   * cover images for the {@code client-galleries} tile, where the typical visibility is UNLISTED —
   * a LISTED-only lookup would return no candidates and the tile would render with no cover.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findClientGalleriesForAdminCovers() {
    log.debug("Finding admin-cover candidates for client galleries");
    List<CollectionEntity> collections =
        collectionRepository.findClientGalleriesByVisibilityIn(
            List.of(CollectionVisibility.LISTED, CollectionVisibility.UNLISTED));
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  /**
   * Return child collections referenced by the "home" parent collection. Used by AdminHomeService
   * to pick a cover image for the home tile. Returns an empty list if the home collection does not
   * exist.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findChildCollectionsForHome() {
    return collectionRepository
        .findBySlug(HOME_SLUG)
        .map(home -> collectionRepository.findReferencedCollectionsByParentId(home.getId()))
        .map(collectionProcessingUtil::batchConvertToBasicModels)
        .orElseGet(List::of);
  }

  /**
   * Return all visible collections that have a cover image set. Used by AdminHomeService to pick a
   * cover for the all-collections tile.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findAllListedWithCovers() {
    List<CollectionEntity> entities = collectionRepository.findAllListedWithCovers();
    return collectionProcessingUtil.batchConvertToBasicModels(entities);
  }

  /**
   * Page of LISTED collections at a location, plus the location's orphan images -- those at the
   * location but in none of its collections.
   *
   * <p>Orphan exclusion needs the ids of ALL listed collections at the location. Reusing the ids
   * already in hand instead of re-querying is only valid when this page actually holds every
   * collection, which requires being on the FIRST page. Past it, the offset is at or beyond {@code
   * totalCollections}, so {@code collectionEntities} is empty; both orphan queries respond to an
   * empty exclusion list by omitting their {@code NOT EXISTS} clause, and every image at the
   * location comes back as an "orphan" -- including ones sitting in the collections listed right
   * above them. Hence the {@code collectionPage == 0} half of the condition.
   */
  @Transactional(readOnly = true)
  public LocationPageResponse getLocationPage(
      String locationName, int collectionPage, int collectionSize, int imagePage, int imageSize) {
    log.debug("Getting location page for: {}", locationName);

    long totalCollections = collectionRepository.countListedByLocationName(locationName);
    int collectionOffset = collectionPage * collectionSize;
    List<CollectionEntity> collectionEntities =
        collectionRepository.findListedByLocationName(
            locationName, collectionSize, collectionOffset);

    List<CollectionModel> collections =
        collectionProcessingUtil.batchConvertToBasicModels(collectionEntities);

    List<Long> allCollectionIds;
    if (collectionPage == 0 && totalCollections <= collectionSize) {
      allCollectionIds = collectionEntities.stream().map(CollectionEntity::getId).toList();
    } else {
      allCollectionIds = collectionRepository.findListedIdsByLocationName(locationName);
    }

    int imageOffset = imagePage * imageSize;
    List<ContentEntity> orphanEntities =
        contentRepository.findOrphanContentByLocationName(
            locationName, allCollectionIds, imageSize, imageOffset);
    long totalImages =
        contentRepository.countOrphanContentByLocationName(locationName, allCollectionIds);

    List<ContentModel> images = batchConvertOrphans(orphanEntities);

    LocationEntity locationEntity =
        locationRepository.findByLocationName(locationName).orElse(null);
    Records.Location location =
        locationEntity != null
            ? new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug())
            : new Records.Location(null, locationName, SlugUtil.generateSlug(locationName));

    return new LocationPageResponse(location, collections, images, totalCollections, totalImages);
  }

  /**
   * Convert the location's orphan content through the BATCH converters, in the order {@link
   * ContentRepository#findOrphanContentByLocationName} returned.
   *
   * <p>The per-entity {@code convertRegularContentEntityToModel} resolves each block's tags, people
   * and locations with three queries apiece. Its tag and people lookups are guarded by {@code
   * getTags()}/{@code getPeople()} being non-empty and that guard never holds here -- these
   * entities come from {@code findAllByIds}, whose row mappers do not populate either set -- but
   * the location lookup is unguarded, so the default page of 50 cost up to 150 queries. The two
   * batch converters issue their three queries once per kind, making the block count irrelevant.
   *
   * <p>The partition is total because the query pins {@code content_type IN ('IMAGE', 'GIF')};
   * widening that SQL means adding a branch here, or the new type silently vanishes from the page.
   * Re-merging by id against {@code orphanEntities} rather than concatenating the two batches is
   * what preserves the SQL's {@code sort_date DESC} ordering, which images-then-gifs would not.
   */
  private List<ContentModel> batchConvertOrphans(List<ContentEntity> orphanEntities) {
    List<ContentImageEntity> orphanImages =
        orphanEntities.stream()
            .filter(ContentImageEntity.class::isInstance)
            .map(ContentImageEntity.class::cast)
            .toList();
    List<ContentGifEntity> orphanGifs =
        orphanEntities.stream()
            .filter(ContentGifEntity.class::isInstance)
            .map(ContentGifEntity.class::cast)
            .toList();

    Map<Long, ContentModel> byId = new HashMap<>();
    contentModelConverter
        .batchConvertImageEntitiesToModels(orphanImages)
        .forEach(model -> byId.put(model.id(), model));
    contentModelConverter
        .batchConvertGifEntitiesToModels(orphanGifs)
        .forEach(model -> byId.put(model.id(), model));

    return orphanEntities.stream()
        .map(entity -> byId.get(entity.getId()))
        .filter(Objects::nonNull)
        .toList();
  }

  @Transactional(readOnly = true)
  public LocationPageResponse getLocationPageBySlug(
      String slug, int collectionPage, int collectionSize, int imagePage, int imageSize) {
    log.debug("Getting location page by slug: {}", slug);

    LocationEntity locationEntity =
        locationRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Location not found with slug: " + slug));

    return getLocationPage(
        locationEntity.getLocationName(), collectionPage, collectionSize, imagePage, imageSize);
  }

  @Transactional(readOnly = true)
  public CollectionModel findMetaBySlug(String slug) {
    log.debug("Finding collection metadata by slug: {}", slug);
    CollectionEntity entity =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    enforceVisibility(entity, slug, isLocalEnvironment());

    return collectionProcessingUtil.convertToBasicModel(entity);
  }

  /**
   * Collection metadata plus its full, unpaginated content list (fetched via the join table in
   * {@code convertToFullModel}). Use {@link #findMetaBySlug} when the content is not needed.
   */
  @Transactional(readOnly = true)
  public Optional<CollectionModel> findBySlug(String slug) {
    log.debug("Finding collection by slug: {}", slug);

    return collectionRepository.findBySlug(slug).map(collectionProcessingUtil::convertToFullModel);
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public CollectionRequests.UpdateResponse createCollection(
      CollectionRequests.Create createRequest) {
    readCacheInvalidator.markChanged();
    log.debug("Creating new collection: {}", createRequest.title());

    CollectionEntity entity = collectionProcessingUtil.toEntity(createRequest, DEFAULT_PAGE_SIZE);

    CollectionEntity savedEntity = collectionRepository.save(entity);

    List<Long> locationIds =
        collectionProcessingUtil.resolveLocationIds(
            createRequest.locationIds(), createRequest.locationNames());
    if (!locationIds.isEmpty()) {
      locationRepository.saveCollectionLocations(savedEntity.getId(), locationIds);
    }

    return getUpdateCollectionData(savedEntity.getSlug());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public CollectionRequests.UpdateResponse createChildCollection(
      Long parentId, CollectionRequests.Create createRequest) {
    readCacheInvalidator.markChanged();
    log.debug(
        "Creating new child collection: {} under parent: {}", createRequest.title(), parentId);

    CollectionEntity childEntity =
        collectionProcessingUtil.toEntity(createRequest, DEFAULT_PAGE_SIZE);
    CollectionEntity savedChildEntity = collectionRepository.save(childEntity);
    log.info("Created child collection with ID: {}", savedChildEntity.getId());

    List<Long> childLocationIds =
        collectionProcessingUtil.resolveLocationIds(
            createRequest.locationIds(), createRequest.locationNames());
    if (!childLocationIds.isEmpty()) {
      locationRepository.saveCollectionLocations(savedChildEntity.getId(), childLocationIds);
    }

    linkCollectionToParent(parentId, savedChildEntity.getId());

    return getUpdateCollectionData(savedChildEntity.getSlug());
  }

  /**
   * Link an existing collection as a child of a parent collection. Creates the
   * ContentCollectionEntity if needed and adds the join table entry. No-op if already linked. The
   * new child inherits every grant the parent holds, origin preserved.
   *
   * <p>This is not the only writer of a parent-to-child join row -- {@code
   * handleCollectionToCollectionUpdates} builds one inline for the admin Structure tab. Both must
   * run {@link #validateNoLinkCycle} and {@link #propagateGalleryPasswordOnLink}.
   *
   * <p>{@code parentEntity} is declared {@code final} to satisfy checkstyle's
   * VariableDeclarationUsageDistance: it is resolved up front, because it must exist before
   * anything else happens, but is not read until the S6 password propagation at the end.
   */
  @Transactional
  public void linkCollectionToParent(Long parentId, Long childCollectionId) {
    final CollectionEntity parentEntity =
        collectionRepository
            .findById(parentId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Parent collection not found with ID: " + parentId));

    validateNoLinkCycle(parentId, childCollectionId);

    CollectionEntity childEntity =
        collectionRepository
            .findById(childCollectionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Child collection not found with ID: " + childCollectionId));

    ContentCollectionEntity contentCollectionEntity =
        findOrCreateContentCollectionEntity(childEntity);

    Optional<CollectionContentEntity> existing =
        collectionRepository.findContentByCollectionIdAndContentId(
            parentId, contentCollectionEntity.getId());
    if (existing.isPresent()) {
      log.debug("Collection {} already linked to parent {}", childCollectionId, parentId);
      return;
    }

    Integer orderIndex = collectionRepository.getMaxOrderIndexForCollection(parentId);
    orderIndex = (orderIndex != null) ? orderIndex + 1 : 0;

    CollectionContentEntity joinEntry =
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(contentCollectionEntity.getId())
            .orderIndex(orderIndex)
            .visible(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    collectionRepository.saveContent(joinEntry);
    log.info(
        "Linked child collection {} to parent {} at index {}",
        childCollectionId,
        parentId,
        orderIndex);

    roleGrantPropagationService.onChildLinked(parentId, childCollectionId);

    propagateGalleryPasswordOnLink(parentEntity, childEntity);
  }

  /**
   * S6: linkage is a password-propagation trigger, symmetric with the role-grant waterfall. {@code
   * updateGalleryAccess} was the only writer, so a client gallery linked after the parent's
   * password was set kept a null password -- and a null password means {@code isPasswordProtected}
   * is false, content is never stripped, UNLISTED direct-slug access is permitted, and the download
   * gate is skipped. Under a derived parent model, password-then-link is the routine ordering.
   *
   * <p>Deliberately independent of the link's {@code visible} flag: a hidden membership still makes
   * the child a gated gallery's descendant, and this direction only ever adds protection.
   */
  private void propagateGalleryPasswordOnLink(
      CollectionEntity parentEntity, CollectionEntity childEntity) {
    if (parentEntity.getGalleryPassword() == null
        || !childEntity.isClient()
        || childEntity.getGalleryPassword() != null) {
      return;
    }
    collectionRepository.updateGalleryPassword(
        childEntity.getId(), parentEntity.getGalleryPassword());
    log.info(
        "Propagated parent (id={}) gallery password to newly linked client child (id={}, slug={})",
        parentEntity.getId(),
        childEntity.getId(),
        childEntity.getSlug());
  }

  /**
   * Reject a link that would close a cycle. Called from both writers that create a parent-to-child
   * join row: {@link #linkCollectionToParent} (createChildCollection, the staging auto-link and tag
   * conversion) and {@code handleCollectionToCollectionUpdates} (the admin Structure tab, which
   * builds the join row inline).
   *
   * <p>The pre-existing {@code validateNoParentCycles} runs only on the inverse {@code parents}
   * path and catches only self- and 2-cycles by its own admission. This is a full ancestor walk,
   * cycle-guarded with a visited set exactly like {@code RoleGrantPropagationService#subtreeOf}, so
   * an existing cycle in the data cannot make the guard itself loop. A cycle matters because every
   * member becomes simultaneously an ancestor and a descendant of every other, so role grants merge
   * across it -- a client gallery's grants would waterfall onto a public collection.
   */
  private void validateNoLinkCycle(Long parentId, Long childCollectionId) {
    if (parentId.equals(childCollectionId)) {
      throw new IllegalArgumentException(
          "A collection cannot be its own parent (id=" + parentId + ")");
    }
    Set<Long> visited = new HashSet<>();
    visited.add(parentId);
    Deque<Long> pending = new ArrayDeque<>(parentIdsOf(parentId));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      if (current.equals(childCollectionId)) {
        throw new IllegalArgumentException(
            "Cycle detected: collection "
                + childCollectionId
                + " is already an ancestor of "
                + parentId
                + " and cannot also be its child");
      }
      pending.addAll(parentIdsOf(current));
    }
  }

  /** Ids of every collection referencing this one as a child, regardless of link visibility. */
  private List<Long> parentIdsOf(Long collectionId) {
    return collectionRepository.findAllParentCollectionsByChildId(collectionId).stream()
        .map(CollectionEntity::getId)
        .toList();
  }

  /**
   * Find the raw {@link CollectionEntity} by ID. Used by admin/download flows that need direct
   * entity access (e.g. updating {@code galleryPassword}, reading bucket-relative S3 keys). Throws
   * {@link ResourceNotFoundException} when no row matches.
   */
  @Transactional(readOnly = true)
  public CollectionEntity findEntityById(Long id) {
    log.debug("Finding collection entity by ID: {}", id);
    return collectionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + id));
  }

  /**
   * Find the raw {@link CollectionEntity} by slug. Used by download endpoints that key off the
   * public slug. Throws {@link ResourceNotFoundException} when no row matches.
   */
  @Transactional(readOnly = true)
  public CollectionEntity findEntityBySlug(String slug) {
    log.debug("Finding collection entity by slug: {}", slug);
    return collectionRepository
        .findBySlug(slug)
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found with slug: " + slug));
  }

  /**
   * Decide whether an incoming request is authorized to read the gated content of a gallery.
   * Encapsulates both the per-slug cookie check and the shared password-fingerprint cookie check
   * (the latter is what makes a PARENT password also unlock its propagated CLIENT_GALLERY children,
   * and vice versa, without re-prompting). Returns {@code true} for unprotected or missing
   * collections — the GET handler still returns 200 with the stripped/empty model.
   *
   * <p>The grant check reads the whole principal, not just {@code CurrentUser.userId()}. Reading
   * only the id dropped {@code isAdmin} before the check ran, so an admin holding no role on a
   * protected gallery was sent to the password prompt (working rule 20: an admin is the owner and
   * is never password-gated). {@link AuthPrincipal#isRealUser} screens first because a share-link
   * holder resolves GENERAL through {@code effectiveLevel}, and a share link must not be a second
   * way past this prompt.
   */
  @Transactional(readOnly = true)
  public boolean isGalleryAccessAuthorized(
      String slug, jakarta.servlet.http.HttpServletRequest request) {
    return collectionRepository
        .findBySlug(slug)
        .map(
            entity -> {
              AuthPrincipal principal = CurrentUser.principal();
              if (AuthPrincipal.isRealUser(principal)
                  && collectionAccessService.canView(principal, entity.getId())) {
                return true;
              }
              return GalleryAccessCookies.hasValidAccess(
                  request, slug, entity.getGalleryPassword(), clientGalleryAuthService);
            })
        .orElse(true);
  }

  /**
   * Apply one collection update: basic fields, then tags, people, child collections, siblings and
   * parents, each via the prev/new/remove pattern.
   *
   * <p>Identity fields are captured before mutation because the shared {@code generalMetadata}
   * cache embeds the collection id/title/slug list, so it only needs invalidating when one of those
   * actually changes. The previous blanket {@link CacheEvict} fired on every save that merely
   * included a title or slug in its payload -- which the manage page always does -- forcing a cold
   * rebuild of all tags/people/locations metadata mid-save. {@code applyBasicUpdates} has already
   * mutated the managed entity by then, so the comparison reads it directly.
   *
   * <p>Returns a lightweight model rather than reloading all content, to avoid N+1 queries; the
   * frontend refetches full content when it needs it.
   */
  @Transactional
  public CollectionModel updateContent(Long id, CollectionRequests.Update updateDTO) {
    log.debug("Updating collection with ID: {}", id);

    CollectionEntity entity =
        collectionRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with ID: " + id));

    final String previousTitle = entity.getTitle();
    final String previousSlug = entity.getSlug();

    collectionProcessingUtil.applyBasicUpdates(entity, updateDTO);

    if (updateDTO.tags() != null) {
      updateCollectionTags(entity, updateDTO.tags());
    }

    if (updateDTO.people() != null) {
      updateCollectionPeople(entity, updateDTO.people());
    }

    if (updateDTO.collections() != null) {
      handleCollectionToCollectionUpdates(entity, updateDTO.collections());
    }

    handleSiblingUpdates(entity.getId(), updateDTO.siblings());

    handleParentCollectionUpdates(entity, updateDTO.parents());

    long totalBlocks = collectionRepository.countContentByCollectionId(entity.getId());
    entity.setTotalContent((int) totalBlocks);

    CollectionEntity savedEntity = collectionRepository.save(entity);

    if (!Objects.equals(previousTitle, entity.getTitle())
        || !Objects.equals(previousSlug, entity.getSlug())) {
      evictGeneralMetadataCache();
    }

    return collectionProcessingUtil.convertToBasicModel(savedEntity);
  }

  /**
   * Manually evict the shared {@code generalMetadata} cache. Used from methods that update it via
   * self-invocation, where Spring's proxy-based {@link CacheEvict} cannot intercept.
   *
   * <p>Also drops the CDN copy: a title or slug change is exactly what the cached collection list
   * renders. This is the path a plain "save the collection" takes, and it carries no {@link
   * CacheEvict} annotation, so anything keyed on that annotation would miss it.
   */
  private void evictGeneralMetadataCache() {
    var cache = cacheManager.getCache("generalMetadata");
    if (cache != null) {
      cache.clear();
      log.debug("Evicted generalMetadata cache after collection identity change");
    }
    readCacheInvalidator.markChanged();
  }

  @Transactional
  public CollectionRequests.UpdateResponse updateContentWithMetadata(
      Long id, CollectionRequests.Update updateDTO) {
    log.debug("Updating collection with ID: {} (with metadata response)", id);

    long writeStart = System.nanoTime();
    CollectionModel updatedCollection = updateContent(id, updateDTO);
    long writeEnd = System.nanoTime();

    CollectionRequests.UpdateResponse response =
        getUpdateCollectionData(updatedCollection.getSlug());
    long refetchEnd = System.nanoTime();

    log.info(
        "updateContentWithMetadata timing [collection {}]: write={}ms refetch={}ms total={}ms",
        id,
        (writeEnd - writeStart) / 1_000_000,
        (refetchEnd - writeEnd) / 1_000_000,
        (refetchEnd - writeStart) / 1_000_000);

    return response;
  }

  /**
   * Set the rating for a collection. Throws ResourceNotFoundException if no row matched.
   *
   * @param id collection id
   * @param rating 0-5 (nullable to clear)
   */
  @Transactional
  public void updateRating(Long id, Integer rating) {
    int rows = collectionRepository.updateRating(id, rating);
    if (rows == 0) {
      throw new ResourceNotFoundException("Collection not found: " + id);
    }
  }

  /**
   * Apply one collaborator image-edit batch as a single transaction.
   *
   * <p>The cross-collection guard runs first, so a rejected batch writes nothing. Canonical fields
   * (title/caption/alt/rating) then route through the one ContentService.updateImages
   * implementation and reach every collection the image appears in; the scoped {@code visible} flag
   * writes only this collection's join row.
   *
   * <p>Both halves used to run from the controller as separate transactions. A failure part-way
   * through the visibility loop left the canonical edits committed and some join rows updated, with
   * no way to tell which. Holding them in one transaction makes the batch all-or-nothing.
   *
   * @param collectionId the authorized collection scope
   * @param updates one entry per image, already validated and non-empty
   * @return the ContentService response, plus a {@code visibleUpdated} count
   */
  @Transactional
  public Map<String, Object> applyCollaboratorImageEdits(
      Long collectionId, List<CollaboratorRequests.CollaboratorImageUpdate> updates) {
    List<Long> ids =
        updates.stream().map(CollaboratorRequests.CollaboratorImageUpdate::id).toList();
    requireImagesInCollection(collectionId, ids);

    List<ContentImageUpdateRequest> canonical =
        updates.stream()
            .filter(CollaboratorRequests.CollaboratorImageUpdate::hasCanonicalEdit)
            .map(CollaboratorRequests.CollaboratorImageUpdate::toImageUpdate)
            .toList();
    Map<String, Object> response =
        canonical.isEmpty()
            ? new HashMap<>()
            : new HashMap<>(contentService.updateImages(canonical));

    int visibleUpdated = 0;
    for (CollaboratorRequests.CollaboratorImageUpdate update : updates) {
      if (update.visible() != null) {
        updateImageVisibility(collectionId, update.id(), update.visible());
        visibleUpdated++;
      }
    }
    response.put("visibleUpdated", visibleUpdated);
    return response;
  }

  /**
   * Cross-collection guard for collaborator image edits: every content id must have a
   * collection_content row in {@code collectionId}. 404 when the collection itself is unknown;
   * otherwise a 403 AccessDeniedException naming the outsiders, thrown BEFORE any write. Without
   * this, a collaborator scoped to gallery X could submit image ids from gallery Y and edit
   * canonical fields site-wide.
   */
  @Transactional(readOnly = true)
  public void requireImagesInCollection(Long collectionId, List<Long> contentIds) {
    collectionRepository
        .findById(collectionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found with ID: " + collectionId));
    Set<Long> members =
        collectionRepository.findImageContentByCollectionIds(List.of(collectionId)).stream()
            .map(CollectionContentEntity::getContentId)
            .collect(Collectors.toSet());
    List<Long> outsiders = contentIds.stream().filter(id -> !members.contains(id)).toList();
    if (!outsiders.isEmpty()) {
      throw new AccessDeniedException(
          "Images " + outsiders + " are not part of collection " + collectionId);
    }
  }

  /** Set the per-collection visibility of one image's membership row (scoped, not canonical). */
  @Transactional
  public void updateImageVisibility(Long collectionId, Long contentId, boolean visible) {
    int rows =
        collectionRepository.updateContentVisibleForContent(collectionId, contentId, visible);
    if (rows == 0) {
      throw new ResourceNotFoundException(
          "No membership row for content " + contentId + " in collection " + collectionId);
    }
  }

  /**
   * Replace the entire {@code collection_people} list. Membership (via roles) is not
   * auto-materialized here — it must be granted explicitly via the /admin Users module.
   */
  @Transactional
  public void setCollectionPeople(Long collectionId, List<Long> personIds) {
    collectionPeopleRepository.setPeopleForCollection(collectionId, personIds);
  }

  /**
   * Auto-fill {@code collection_people} from the distinct people tagged on the collection's visible
   * images. Manual {@link #setCollectionPeople} can still overwrite this afterwards.
   */
  @Transactional
  public void regeneratePeopleFromContents(Long collectionId) {
    List<Long> distinctPersonIds =
        contentRepository.findDistinctPersonIdsInCollection(collectionId);
    collectionPeopleRepository.setPeopleForCollection(collectionId, distinctPersonIds);
  }

  /**
   * Delete a collection and detach everything that references it.
   *
   * <p>Parent collections that reference this one as a child are captured BEFORE the
   * back-references are removed, then each parent's {@code totalContent} is recounted so its stored
   * count stays accurate.
   *
   * <p>This collection's own content membership and its tags are dissociated; the content itself is
   * reusable and is NOT deleted. {@code collection_locations}, {@code collection_people} and {@code
   * collection_sibling} rows go via {@code ON DELETE CASCADE} when the collection row is deleted.
   */
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public void deleteCollection(Long id) {
    readCacheInvalidator.markChanged();
    log.debug("Deleting collection with ID: {}", id);

    if (collectionRepository.findById(id).isEmpty()) {
      throw new ResourceNotFoundException("Collection not found with ID: " + id);
    }

    List<CollectionEntity> parents = collectionRepository.findAllParentCollectionsByChildId(id);
    contentRepository.deleteContentCollectionsReferencing(id);
    for (CollectionEntity parent : parents) {
      recountParentTotalContent(parent);
    }

    collectionRepository.deleteContentByCollectionId(id);
    tagRepository.deleteCollectionTags(id);
    log.debug("Disassociated content, tags, and parent references for collection ID: {}", id);

    collectionRepository.deleteById(id);
    log.info("Successfully deleted collection with ID: {}", id);
  }

  @Transactional(readOnly = true)
  public Page<CollectionModel> getAllCollections(Pageable pageable) {
    log.debug("Getting all collections with pagination");

    long totalElements = collectionRepository.countAllCollections();

    int offset = pageable.getPageNumber() * pageable.getPageSize();
    List<CollectionEntity> paginatedCollections =
        collectionRepository.findAllByOrderByCollectionDateDesc(pageable.getPageSize(), offset);

    List<CollectionModel> models =
        collectionProcessingUtil.batchConvertToBasicModels(paginatedCollections);

    return new PageImpl<>(models, pageable, totalElements);
  }

  @Transactional(readOnly = true)
  public Page<CollectionModel> getVisibleCollections(Pageable pageable) {
    log.debug("Getting visible collections with pagination");

    long totalElements = collectionRepository.countVisibleCollections();

    int offset = pageable.getPageNumber() * pageable.getPageSize();
    List<CollectionEntity> paginatedCollections =
        collectionRepository.findAllListedOrdered(pageable.getPageSize(), offset);

    List<CollectionModel> models =
        collectionProcessingUtil.batchConvertToBasicModels(paginatedCollections);

    return new PageImpl<>(models, pageable, totalElements);
  }

  /**
   * Full manage-page payload for one collection: the collection with its unpaginated content, the
   * shared general metadata, the images aggregated from every referenced child collection, and the
   * admin-only fields ({@code galleryPassword}, {@code recipientEmails}) the manage page displays
   * and edits.
   *
   * <p>General metadata is fetched through the Spring proxy ({@code selfProvider}) so the {@link
   * Cacheable} on {@link #getGeneralMetadata} is honored. A direct {@code
   * this.getGeneralMetadata()} is self-invoked, bypasses the cache, and re-runs every metadata
   * query on each request.
   */
  @Transactional(readOnly = true)
  public CollectionRequests.UpdateResponse getUpdateCollectionData(String slug) {
    log.debug("Getting update collection data for slug: {}", slug);

    long contentStart = System.nanoTime();
    CollectionModel collection =
        findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));
    long contentEnd = System.nanoTime();

    final GeneralMetadataDTO metadata = selfProvider.getObject().getGeneralMetadata();
    long metadataEnd = System.nanoTime();

    log.info(
        "getUpdateCollectionData timing [slug {}]: contentLoad={}ms metadata={}ms",
        slug,
        (contentEnd - contentStart) / 1_000_000,
        (metadataEnd - contentEnd) / 1_000_000);

    List<ContentModels.Image> childCollectionImages = null;
    CollectionEntity entity =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    if (collection.getContent() != null) {
      List<Long> childCollectionIds =
          collection.getContent().stream()
              .filter(c -> c instanceof ContentModels.Collection)
              .map(c -> ((ContentModels.Collection) c).referencedCollectionId())
              .filter(Objects::nonNull)
              .toList();

      childCollectionImages =
          collectionProcessingUtil.loadImagesFromChildCollections(childCollectionIds);
      log.debug(
          "Aggregated {} images from {} child collections for parent collection '{}'",
          childCollectionImages.size(),
          childCollectionIds.size(),
          slug);
    }

    collection.setGalleryPassword(entity.getGalleryPassword());
    collection.setRecipientEmails(entity.getRecipientEmails());

    collection.setParents(
        collectionRepository.findAllParentCollectionsByChildId(entity.getId()).stream()
            .map(
                p ->
                    new Records.CollectionList(
                        p.getId(),
                        p.getTitle(),
                        p.getSlug(),
                        p.getCollectionDate(),
                        null,
                        p.isClient(),
                        p.isBlog()))
            .toList());

    List<Long> allChildCollectionIds =
        collectionRepository.findAllReferencedCollectionIdsByParentId(entity.getId());

    return new CollectionRequests.UpdateResponse(
        collection,
        metadata,
        childCollectionImages,
        !allChildCollectionIds.isEmpty(),
        allChildCollectionIds);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "generalMetadata", unless = "#result == null")
  public GeneralMetadataDTO getGeneralMetadata() {
    log.debug("Getting general metadata (cache miss)");

    List<Records.Tag> tags = metadataService.getAllTags();
    List<Records.Person> people = metadataService.getAllPeople();
    List<Records.Location> locations = metadataService.getAllLocations();
    List<Records.Camera> cameras = metadataService.getAllCameras();
    List<Records.Lens> lenses = metadataService.getAllLenses();
    List<ContentFilmTypeModel> filmTypes = metadataService.getAllFilmTypes();

    List<Records.CollectionList> collections = collectionRepository.findCollectionListEntries();

    List<Records.FilmFormatOption> filmFormats =
        Arrays.stream(FilmFormat.values()).map(Records.FilmFormatOption::of).toList();

    return new GeneralMetadataDTO(
        tags, people, locations, collections, cameras, lenses, filmTypes, filmFormats);
  }

  /**
   * Update collection tags using prev/new/remove pattern. Uses shared utility method from
   * ContentMutationUtil.
   *
   * <p>Current tags are loaded as FULLY populated entities. {@link TagEntity} keys {@code
   * equals}/{@code hashCode} on {@code tagName}, so a partially built tag carrying only an id
   * hashes to 0 and never compares equal to anything. This method used to fabricate exactly such
   * id-only entities, which meant a RETAINED tag — one present in both the current set and the
   * request's {@code prev} list, where {@code updateTags} re-adds it fully loaded from the
   * repository — landed in two different hash buckets and survived twice. That duplicate id then
   * collided on the {@code collection_tags} primary key, and since {@code updateContent} is
   * transactional the rollback discarded the entire save rather than just the tags. Loading full
   * entities keeps this path identical to the content-side equivalent, {@link
   * ContentMutationUtil#updateImageTagsOptimized}, which never had the bug because it already
   * passes fully loaded tags.
   *
   * @param collection The collection to update
   * @param tagUpdate The tag update containing remove/prev/newValue operations
   */
  private void updateCollectionTags(
      CollectionEntity collection, CollectionRequests.TagUpdate tagUpdate) {
    Set<TagEntity> currentTags =
        new HashSet<>(tagRepository.findCollectionTags(collection.getId()));

    Set<TagEntity> updatedTags =
        contentMutationUtil.updateTags(
            currentTags, tagUpdate, null // No tracking needed for collection updates
            );

    List<Long> updatedTagIds =
        updatedTags.stream()
            .map(TagEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    tagRepository.saveCollectionTags(collection.getId(), updatedTagIds);
    log.info("Updated tags for collection {}", collection.getId());
  }

  /**
   * Update collection people using prev/new/remove pattern. Uses shared utility method from
   * ContentMutationUtil.
   *
   * <p>Current people are loaded as FULLY populated entities, for the same reason {@link
   * #updateCollectionTags} loads full tags. {@link ContentPersonEntity} keys {@code equals}/{@code
   * hashCode} on {@code personName}, so a partially built person carrying only an id hashes to 0
   * and never compares equal to anything. This method used to fabricate exactly such id-only
   * entities, which meant a RETAINED person -- one present in both the current set and the
   * request's {@code prev} list, where {@code updatePeople} re-adds it fully loaded from the
   * repository -- landed in two different hash buckets and survived twice.
   *
   * <p>That duplicate id never reached the {@code collection_people} primary key only because
   * {@link CollectionPeopleRepository#setPeopleForCollection} de-duplicates its insert batch. The
   * tag path had no such backstop, which is why the identical defect there rolled back entire
   * saves. Loading full entities fixes the cause rather than relying on the DAO to absorb it.
   *
   * @param collection The collection to update
   * @param personUpdate The person update containing remove/prev/newValue operations
   */
  private void updateCollectionPeople(
      CollectionEntity collection, CollectionRequests.PersonUpdate personUpdate) {
    Set<ContentPersonEntity> currentPeople =
        new HashSet<>(collectionPeopleRepository.findPeopleForCollection(collection.getId()));

    Set<ContentPersonEntity> updatedPeople =
        contentMutationUtil.updatePeople(
            currentPeople, personUpdate, null // No tracking needed for collection updates
            );

    List<Long> updatedPersonIds =
        updatedPeople.stream()
            .map(ContentPersonEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    collectionPeopleRepository.setPeopleForCollection(collection.getId(), updatedPersonIds);
    log.info("Updated people for collection {}", collection.getId());
  }

  /**
   * Handle collection-to-collection relationship updates. This manages which child collections
   * belong to this parent collection, in three phases: {@code remove} unassociates children, {@code
   * newValue} adds them, and {@code prev} updates existing associations (orderIndex, visible).
   *
   * <p>Role grants waterfall on every one of those phases:
   *
   * <ul>
   *   <li>Unlink: each unlinked child subtree loses the grants it inherited through this parent
   *       (origins at or above it); grants with origins inside the subtree survive.
   *   <li>Link: a visibly linked child inherits the parent's grants. Hidden links do not waterfall,
   *       mirroring the {@code cc.visible} gate used by propagation and the V47 backfill.
   *   <li>Visibility toggle: flipping an existing link re-syncs the child subtree's inherited
   *       grants (materialize on reveal, strip on hide). Without it the toggle bypasses propagation
   *       and drifts from the {@code cc.visible} gate. Applies to both the {@code newValue} and
   *       {@code prev} paths.
   * </ul>
   *
   * <p>S5: the {@code newValue} branch creates a join row directly rather than going through {@link
   * #linkCollectionToParent}, so the ancestor walk has to run here too. Without it, two admin saves
   * (A.collections += B, then B.collections += A) close a cycle and {@code onChildLinked} merges
   * role grants across both subtrees.
   *
   * <p>S6: same password-propagation trigger as {@link #linkCollectionToParent}. A client-gallery
   * child linked under a passworded wrapper here would otherwise keep a null {@code
   * gallery_password}, leaving {@code isPasswordProtected} false and the download gate skipped.
   *
   * @param parentCollection The collection being updated (parent collection)
   * @param collectionUpdates The collection update containing remove/prev/newValue operations
   */
  private void handleCollectionToCollectionUpdates(
      CollectionEntity parentCollection, CollectionRequests.CollectionUpdate collectionUpdates) {
    log.debug(
        "Handling collection-to-collection updates for collection {}", parentCollection.getId());

    if (collectionUpdates.remove() != null && !collectionUpdates.remove().isEmpty()) {
      List<ContentCollectionEntity> contentColEntities =
          findCurrentContentCollections(parentCollection, collectionUpdates.remove());

      if (!contentColEntities.isEmpty()) {
        List<Long> contentIdsToRemove =
            contentColEntities.stream().map(ContentCollectionEntity::getId).toList();

        collectionRepository.removeContentFromCollection(
            parentCollection.getId(), contentIdsToRemove);
        log.info(
            "Removed {} collection references from parent collection {}",
            contentIdsToRemove.size(),
            parentCollection.getId());

        contentColEntities.stream()
            .map(ContentCollectionEntity::getReferencedCollection)
            .filter(Objects::nonNull)
            .map(CollectionEntity::getId)
            .forEach(
                childId ->
                    roleGrantPropagationService.onChildUnlinked(parentCollection.getId(), childId));
      } else {
        log.debug(
            "No matching content collections found to remove from collection {}",
            parentCollection.getId());
      }
    }

    if (collectionUpdates.newValue() != null && !collectionUpdates.newValue().isEmpty()) {
      for (Records.ChildCollection childCollection : collectionUpdates.newValue()) {
        CollectionEntity childCollectionEntity =
            collectionRepository
                .findById(childCollection.collectionId())
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Child collection not found: " + childCollection.collectionId()));

        validateNoLinkCycle(parentCollection.getId(), childCollectionEntity.getId());

        ContentCollectionEntity existingContentCollection =
            findOrCreateContentCollectionEntity(childCollectionEntity);

        Integer maxIndex =
            collectionRepository.getMaxOrderIndexForCollection(parentCollection.getId());
        Integer orderIndex =
            childCollection.orderIndex() != null
                ? childCollection.orderIndex()
                : (maxIndex != null ? maxIndex + 1 : 0);

        CollectionContentEntity existingJoinEntry =
            collectionRepository
                .findContentByCollectionIdAndContentId(
                    parentCollection.getId(), existingContentCollection.getId())
                .orElse(null);

        if (existingJoinEntry == null) {
          CollectionContentEntity newEntry =
              CollectionContentEntity.builder()
                  .collectionId(parentCollection.getId())
                  .contentId(existingContentCollection.getId())
                  .orderIndex(orderIndex)
                  .visible(childCollection.visible() != null ? childCollection.visible() : true)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build();

          collectionRepository.saveContent(newEntry);
          log.info(
              "Added collection {} to parent collection {} at index {}",
              childCollectionEntity.getId(),
              parentCollection.getId(),
              orderIndex);

          if (Boolean.TRUE.equals(newEntry.getVisible())) {
            roleGrantPropagationService.onChildLinked(
                parentCollection.getId(), childCollectionEntity.getId());
          }

          propagateGalleryPasswordOnLink(parentCollection, childCollectionEntity);
        } else {
          if (childCollection.orderIndex() != null) {
            collectionRepository.updateContentOrderIndex(
                existingJoinEntry.getId(), childCollection.orderIndex());
          }
          if (childCollection.visible() != null) {
            boolean wasVisible = Boolean.TRUE.equals(existingJoinEntry.getVisible());
            collectionRepository.updateContentVisible(
                existingJoinEntry.getId(), childCollection.visible());
            roleGrantPropagationService.onChildVisibilityToggled(
                parentCollection.getId(),
                childCollectionEntity.getId(),
                wasVisible,
                childCollection.visible());
          }
          log.info(
              "Updated existing collection reference in parent collection {}",
              parentCollection.getId());
        }
      }
    }

    if (collectionUpdates.prev() != null && !collectionUpdates.prev().isEmpty()) {
      for (Records.ChildCollection prev : collectionUpdates.prev()) {
        ContentCollectionEntity contentCollectionEntity =
            findContentCollectionEntityByReferencedCollectionId(prev.collectionId());

        if (contentCollectionEntity == null) {
          log.warn(
              "No ContentCollectionEntity found for collection ID {} in prev update for parent collection {}",
              prev.collectionId(),
              parentCollection.getId());
          continue;
        }

        Optional<CollectionContentEntity> joinEntryOpt =
            collectionRepository.findContentByCollectionIdAndContentId(
                parentCollection.getId(), contentCollectionEntity.getId());

        if (joinEntryOpt.isPresent()) {
          CollectionContentEntity joinEntry = joinEntryOpt.get();
          if (prev.orderIndex() != null) {
            collectionRepository.updateContentOrderIndex(joinEntry.getId(), prev.orderIndex());
          }
          if (prev.visible() != null) {
            boolean wasVisible = Boolean.TRUE.equals(joinEntry.getVisible());
            collectionRepository.updateContentVisible(joinEntry.getId(), prev.visible());
            roleGrantPropagationService.onChildVisibilityToggled(
                parentCollection.getId(), prev.collectionId(), wasVisible, prev.visible());
          }
          log.debug(
              "Updated existing collection reference {} in parent collection {}",
              contentCollectionEntity.getId(),
              parentCollection.getId());
        }
      }
    }
  }

  /**
   * Apply sibling updates. Each {@code newValue} entry's collectionId is written via {@code
   * setSibling}, mutual unless the entry sets {@code mutual} false; each {@code remove} id is
   * deleted bidirectionally, which is correct for both shapes because a one-way link has no reverse
   * row to delete. Self-links are skipped defensively (the DB CHECK also blocks them). No-op when
   * {@code siblings} is null.
   */
  private void handleSiblingUpdates(Long parentId, CollectionRequests.CollectionUpdate siblings) {
    if (siblings == null) {
      return;
    }
    if (siblings.remove() != null) {
      for (Long siblingId : siblings.remove()) {
        if (siblingId == null || siblingId.equals(parentId)) {
          continue;
        }
        collectionSiblingRepository.removeSibling(parentId, siblingId);
      }
    }
    if (siblings.newValue() != null) {
      for (Records.ChildCollection entry : siblings.newValue()) {
        Long siblingId = entry.collectionId();
        if (siblingId == null || siblingId.equals(parentId)) {
          continue;
        }
        boolean mutual = entry.mutual() == null || entry.mutual();
        collectionSiblingRepository.setSibling(parentId, siblingId, mutual);
      }
    }
    log.info("Applied sibling updates for collection {}", parentId);
  }

  /**
   * Apply parent-collection updates by inverting the request and delegating to {@link
   * #handleCollectionToCollectionUpdates}. Each {@code newValue} parent gains the current
   * collection as a child; each {@code remove} parent drops it. Cycle validation (self-parent +
   * direct 2-cycle) runs first and throws {@link IllegalArgumentException} (mapped to 400 Bad
   * Request). No-op when {@code parents} is null.
   */
  private void handleParentCollectionUpdates(
      CollectionEntity currentCollection, CollectionRequests.CollectionUpdate parents) {
    if (parents == null) {
      return;
    }
    validateNoParentCycles(currentCollection, parents);
    if (parents.newValue() != null) {
      for (Records.ChildCollection entry : parents.newValue()) {
        Long parentId = entry.collectionId();
        if (parentId == null) {
          continue;
        }
        CollectionEntity parent =
            collectionRepository
                .findById(parentId)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Parent collection not found with ID: " + parentId));
        Records.ChildCollection currentAsChild =
            new Records.ChildCollection(
                currentCollection.getId(),
                currentCollection.getTitle(),
                currentCollection.getSlug(),
                null,
                null,
                null);
        handleCollectionToCollectionUpdates(
            parent, new CollectionRequests.CollectionUpdate(null, List.of(currentAsChild), null));
        recountParentTotalContent(parent);
      }
    }
    if (parents.remove() != null) {
      for (Long parentId : parents.remove()) {
        if (parentId == null || parentId.equals(currentCollection.getId())) {
          continue;
        }
        CollectionEntity parent =
            collectionRepository
                .findById(parentId)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Parent collection not found with ID: " + parentId));
        handleCollectionToCollectionUpdates(
            parent,
            new CollectionRequests.CollectionUpdate(
                null, null, List.of(currentCollection.getId())));
        recountParentTotalContent(parent);
      }
    }
    log.info("Applied parent collection updates for collection {}", currentCollection.getId());
  }

  /**
   * Recount a parent collection's join-table membership and persist its {@code totalContent}. The
   * inverse parent-update path mutates each parent's children directly, so without this the
   * parent's stored count drifts until it is next edited (mirrors the recount {@link
   * #updateContent} runs for the edited collection).
   */
  private void recountParentTotalContent(CollectionEntity parent) {
    parent.setTotalContent((int) collectionRepository.countContentByCollectionId(parent.getId()));
    collectionRepository.save(parent);
  }

  /**
   * Reject direct cycles before applying parent updates: a collection cannot be its own parent, and
   * a candidate parent that is already a child of the current collection would form a 2-cycle.
   * Deeper N-cycles are an accepted limitation.
   */
  private void validateNoParentCycles(
      CollectionEntity current, CollectionRequests.CollectionUpdate parents) {
    if (parents == null || parents.newValue() == null) {
      return;
    }
    Set<Long> existingChildIds =
        collectionRepository.findAllReferencedCollectionsByParentId(current.getId()).stream()
            .map(CollectionEntity::getId)
            .collect(Collectors.toSet());
    for (Records.ChildCollection entry : parents.newValue()) {
      Long parentId = entry.collectionId();
      if (parentId == null) {
        continue;
      }
      if (parentId.equals(current.getId())) {
        throw new IllegalArgumentException(
            "A collection cannot be its own parent (id=" + parentId + ")");
      }
      if (existingChildIds.contains(parentId)) {
        throw new IllegalArgumentException(
            "Cycle detected: collection "
                + parentId
                + " is already a child of "
                + current.getId()
                + " and cannot also be a parent");
      }
    }
  }

  /**
   * Find ContentCollectionEntity entries in the parent collection that match the provided IDs.
   * Accepts both content IDs (ContentCollectionEntity.id) and referenced collection IDs for
   * flexibility: the former matches the API response's {@code id} field, the latter its {@code
   * referencedCollectionId} field.
   *
   * <p>One query. This used to read every join row in the parent and then ask the database about
   * each one individually, which cost a query per content block -- and {@code
   * SELECT_CONTENT_COLLECTION} inner-joins {@code content_collection}, so every image in the parent
   * bought an empty result. Removing one sub-collection from a 200-image collection issued 201
   * queries, 200 of them answering nothing.
   *
   * @param parentCollection The parent collection to search in
   * @param idsToRemove IDs to match - can be either ContentCollectionEntity IDs or referenced
   *     collection IDs
   * @return List of ContentCollectionEntity entries that match, empty list if none found
   */
  private List<ContentCollectionEntity> findCurrentContentCollections(
      CollectionEntity parentCollection, List<Long> idsToRemove) {
    if (parentCollection == null || idsToRemove == null || idsToRemove.isEmpty()) {
      return Collections.emptyList();
    }

    List<ContentCollectionEntity> matchingContentCollections =
        contentRepository.findCollectionContentInCollectionMatching(
            parentCollection.getId(), idsToRemove);

    if (matchingContentCollections.isEmpty()) {
      log.debug(
          "No matching ContentCollectionEntity entries found for removal in collection {}"
              + " (searched for IDs: {})",
          parentCollection.getId(),
          idsToRemove);
    } else {
      log.debug(
          "Found {} matching ContentCollectionEntity entries for removal in collection {}",
          matchingContentCollections.size(),
          parentCollection.getId());
    }

    return matchingContentCollections;
  }

  /**
   * Find or create a ContentCollectionEntity for a given referenced collection. Reuses existing
   * ContentCollectionEntity if one already exists for this collection.
   *
   * @param referencedCollection The collection to reference
   * @return The ContentCollectionEntity (existing or newly created)
   */
  private ContentCollectionEntity findOrCreateContentCollectionEntity(
      CollectionEntity referencedCollection) {
    ContentCollectionEntity existing =
        findContentCollectionEntityByReferencedCollectionId(referencedCollection.getId());

    if (existing != null) {
      log.debug(
          "Found existing ContentCollectionEntity {} for collection {}",
          existing.getId(),
          referencedCollection.getId());
      return existing;
    }

    ContentCollectionEntity newContentCollection =
        ContentCollectionEntity.builder()
            .contentType(ContentType.COLLECTION)
            .referencedCollection(referencedCollection)
            .build();

    ContentCollectionEntity saved = contentRepository.saveCollectionContent(newContentCollection);
    log.info(
        "Created new ContentCollectionEntity {} for collection {}",
        saved.getId(),
        referencedCollection.getId());
    return saved;
  }

  /**
   * Find a ContentCollectionEntity that references a collection with the given ID.
   *
   * @param referencedCollectionId The ID of the referenced collection
   * @return The ContentCollectionEntity if found, null otherwise
   */
  private ContentCollectionEntity findContentCollectionEntityByReferencedCollectionId(
      Long referencedCollectionId) {
    return contentRepository
        .findCollectionContentByReferencedCollectionId(referencedCollectionId)
        .orElse(null);
  }

  /**
   * Reorder a collection's content. Every requested content id is validated as belonging to the
   * collection BEFORE any update runs, so a request naming an outsider writes nothing. The reorder
   * itself is a single bulk {@code UPDATE} with a {@code CASE} statement rather than one statement
   * per item.
   */
  @Transactional
  public CollectionModel reorderContent(Long collectionId, CollectionRequests.Reorder request) {
    log.debug(
        "Reordering content in collection {} with {} reorder operations",
        collectionId,
        request.reorders().size());

    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Collection not found with ID: " + collectionId));

    List<CollectionRequests.Reorder.ReorderItem> reorders = request.reorders();

    List<Long> requestedContentIds =
        reorders.stream().map(CollectionRequests.Reorder.ReorderItem::contentId).toList();
    List<CollectionContentEntity> existingEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId);
    Set<Long> validContentIds =
        existingEntries.stream()
            .map(CollectionContentEntity::getContentId)
            .collect(Collectors.toSet());

    for (Long contentId : requestedContentIds) {
      if (!validContentIds.contains(contentId)) {
        throw new IllegalArgumentException(
            "Content with ID " + contentId + " does not belong to collection " + collectionId);
      }
    }

    Map<Long, Integer> contentIdToOrderIndex =
        reorders.stream()
            .collect(
                Collectors.toMap(
                    CollectionRequests.Reorder.ReorderItem::contentId,
                    CollectionRequests.Reorder.ReorderItem::newOrderIndex));

    int totalUpdated =
        collectionRepository.batchUpdateContentOrderIndexes(collectionId, contentIdToOrderIndex);
    log.info("Successfully reordered {} items in collection {}", totalUpdated, collectionId);

    List<CollectionContentEntity> updatedContent =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId);
    long totalElements = updatedContent.size();
    int pageSize = totalElements > 0 ? (int) totalElements : DEFAULT_PAGE_SIZE;
    CollectionModel model =
        collectionProcessingUtil.convertToModel(
            collection, updatedContent, 0, pageSize, totalElements);
    collectionProcessingUtil.populateCollectionsOnContent(model);
    return model;
  }

  /**
   * Enforce visibility on a collection for read endpoints.
   *
   * <ul>
   *   <li>HOME slug always passes (existing exception).
   *   <li>LISTED + UNLISTED both pass for direct slug access.
   *   <li>HIDDEN passes in a local environment, for an admin principal, or for a viewer who reaches
   *       the collection through a role grant; otherwise NotFound. Mirrors the scope the
   *       permission-aware all-collections list surfaces, so a tile a viewer can see is a tile they
   *       can open.
   * </ul>
   */
  private void enforceVisibility(CollectionEntity entity, String slug, boolean isLocalEnvironment) {
    if (HOME_SLUG.equals(slug)) {
      return;
    }
    CollectionVisibility v = entity.getVisibility();
    if (v == CollectionVisibility.HIDDEN
        && !isLocalEnvironment
        && !viewerMaySeeHidden(entity.getId())) {
      log.debug("Blocked HIDDEN collection {} from unauthorized request", slug);
      throw new ResourceNotFoundException("Collection not found with slug: " + slug);
    }
  }

  /** True when the current viewer is an admin or reaches the id through a role grant. */
  private boolean viewerMaySeeHidden(Long collectionId) {
    AuthPrincipal p = CurrentUser.principal();
    if (p == null || p.userId() == null) {
      return false;
    }
    return collectionAccessService.hasAtLeast(p, collectionId, AccessLevel.GENERAL);
  }

  private boolean isLocalEnvironment() {
    return springEnv.acceptsProfiles(Profiles.of("dev"));
  }

  /**
   * Remove child collection content items that reference children the viewer should not see in this
   * context. Default scope (e.g. a directory of portfolios) drops UNLISTED + HIDDEN children so
   * directories don't leak unlisted work. Client-gallery context -- the collection is itself a
   * client gallery, or ANY collection (not just a legacy PARENT) that contains at least one client
   * gallery child -- drops only HIDDEN, so UNLISTED client galleries (the typical visibility for
   * password-protected work) remain visible to viewers who have already navigated into the wrapper.
   * The flag-keyed derivation mirrors {@code findClientGalleriesAndQualifyingParents}, which admits
   * the same derived parents into the listing; keying on {@code type == PARENT} here would strip
   * every child out of a non-PARENT wrapper and render an empty tile. Authentication is enforced
   * upstream; this method runs only for already-authorized responses.
   */
  private void filterNonListedChildCollections(CollectionModel model) {
    if (model == null || model.getContent() == null || model.getContent().isEmpty()) {
      return;
    }

    List<Long> referencedIds =
        model.getContent().stream()
            .filter(ContentModels.Collection.class::isInstance)
            .map(ContentModels.Collection.class::cast)
            .map(ContentModels.Collection::referencedCollectionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (referencedIds.isEmpty()) {
      return;
    }

    List<CollectionEntity> children = collectionRepository.findByIds(referencedIds);

    boolean parentIsProtected = Boolean.TRUE.equals(model.getIsPasswordProtected());

    Set<Long> excludedIds =
        children.stream()
            .filter(c -> isChildExcluded(c, parentIsProtected))
            .map(CollectionEntity::getId)
            .collect(Collectors.toSet());

    if (excludedIds.isEmpty()) {
      return;
    }

    List<ContentModel> filtered =
        model.getContent().stream()
            .filter(
                content -> {
                  if (content instanceof ContentModels.Collection col) {
                    return !excludedIds.contains(col.referencedCollectionId());
                  }
                  return true;
                })
            .collect(Collectors.toList());

    model.setContent(filtered);
    log.debug(
        "Filtered {} child collections from response (parent={}, parentIsProtected={})",
        excludedIds.size(),
        model.getSlug(),
        parentIsProtected);
  }

  /**
   * Whether a referenced child collection must be stripped from a public parent's response.
   *
   * <p>Three independent reasons, each pinned by its own test:
   *
   * <ul>
   *   <li>HIDDEN children never render publicly.
   *   <li>S3: an unprotected parent must not publish a password-protected child's title,
   *       description or cover image, so the block is dropped. {@code ContentModels.Collection} now
   *       carries {@code isPasswordProtected}, so rendering a locked tile here is technically
   *       possible -- it is still deliberately NOT done. The flag exists for the list and tag-view
   *       paths, which serve a child directly rather than nested under someone else's parent; under
   *       an unprotected parent the child's own gate never runs, so dropping stays correct.
   *   <li>S4: the UNLISTED relaxation is per child, not per model. It used to be computed once from
   *       the whole response ("any child is a client gallery"), so linking a single gallery under a
   *       wrapper un-hid every unrelated UNLISTED work-in-progress sibling.
   * </ul>
   */
  private static boolean isChildExcluded(CollectionEntity child, boolean parentIsProtected) {
    if (child.getVisibility() == CollectionVisibility.HIDDEN) {
      return true;
    }
    if (!parentIsProtected && child.getGalleryPassword() != null) {
      return true;
    }
    return !child.isClient() && !child.getVisibility().appearsInLists();
  }

  /**
   * Persists gallery password and recipient list, then sends emails when requested.
   *
   * <p>Three modes, driven by the request:
   *
   * <ul>
   *   <li>password null: clear password and recipients
   *   <li>password set, emails empty: set password, no email
   *   <li>password set, emails non-empty: set password and send one email per recipient
   * </ul>
   *
   * <p>Eligibility is derived, not typed: the target must either be a client gallery itself ({@code
   * is_client}) or reference at least one client-gallery child ({@link
   * edens.zac.portfolio.backend.dao.CollectionRepository#hasClientGalleryChildren}). When {@link
   * GalleryAccessRequest#propagateToChildren()} is {@code true}, the same password is batch-written
   * to every {@code is_client} child referenced by the target; non-client children are skipped.
   * Recipient emails are NOT propagated. Returns {@code GalleryAccessResponse(saved=false,
   * reason="not-eligible-type")} for an ineligible target.
   *
   * <p>Eligibility gates only the SET path (D8). A {@code null} password is accepted on ANY
   * collection, and the clear runs first, unconditionally: this method is the only writer that can
   * clear {@code gallery_password} (see {@code CollectionRepository.save}, which omits the column
   * on UPDATE), so a gated clear path would strand a row behind a password nothing could remove.
   * Concretely, that strands every row holding an enforced password while failing the derived
   * eligibility test below -- a wrapper whose last client-gallery child was unlinked, and the
   * {@code gallery_password IS NOT NULL AND is_client = false} rows U0's reconnaissance enumerates.
   * Such a row keeps serving behind a password no endpoint can remove. Clearing cannot widen access
   * beyond "no password", so it needs no gate.
   */
  @Transactional
  public GalleryAccessResponse updateGalleryAccess(Long id, GalleryAccessRequest request) {
    CollectionEntity entity = findEntityById(id);

    if (request.password() == null) {
      collectionRepository.saveGalleryAccess(id, null, List.of());
      log.info("Cleared gallery password and recipients (id={}, slug={})", id, entity.getSlug());
      return new GalleryAccessResponse(true, false, null, null, List.of());
    }

    if (!entity.isClient() && !collectionRepository.hasClientGalleryChildren(id)) {
      log.warn(
          "Refusing gallery-access update on ineligible collection (id={}, slug={})",
          id,
          entity.getSlug());
      return new GalleryAccessResponse(false, false, "not-eligible-type", null, List.of());
    }

    List<String> emails =
        request.emails() != null && !request.emails().isEmpty() ? request.emails() : List.of();

    collectionRepository.saveGalleryAccess(id, request.password(), emails);
    log.info(
        "Set gallery password (id={}, slug={}, recipients={})",
        id,
        entity.getSlug(),
        emails.size());

    propagatePasswordToChildrenIfRequested(entity, request);

    if (emails.isEmpty()) {
      return new GalleryAccessResponse(true, false, null, request.password(), List.of());
    }

    boolean allSent = true;
    String firstFailureReason = null;
    for (String email : emails) {
      EmailService.SendResult result =
          emailService.sendGalleryPasswordEmail(
              email, entity.getTitle(), entity.getSlug(), request.password());
      if (!result.sent() && allSent) {
        allSent = false;
        firstFailureReason = result.reason();
      }
    }

    return new GalleryAccessResponse(
        true, allSent, allSent ? null : firstFailureReason, request.password(), emails);
  }

  /**
   * When {@code request.propagateToChildren()} is {@code true}, batch-update the same password on
   * every {@code is_client} child referenced by the parent. Non-client children are skipped.
   *
   * <p>There is no parent-side gate any more. Parent-ness is derived from the content graph, so "is
   * this a wrapper" is not a question this method can or should ask -- eligibility already answered
   * it upstream, and a target with no client children simply writes nothing.
   */
  private void propagatePasswordToChildrenIfRequested(
      CollectionEntity parent, GalleryAccessRequest request) {
    if (!Boolean.TRUE.equals(request.propagateToChildren())) {
      log.debug(
          "Skipping password propagation (parentId={}, propagate={})",
          parent.getId(),
          request.propagateToChildren());
      return;
    }
    List<CollectionEntity> children =
        collectionRepository.findAllReferencedCollectionsByParentId(parent.getId());
    long clientGalleryCount = children.stream().filter(CollectionEntity::isClient).count();
    log.info(
        "Propagating password from parent (id={}, slug={}): {} children found, {} are client galleries",
        parent.getId(),
        parent.getSlug(),
        children.size(),
        clientGalleryCount);
    for (CollectionEntity child : children) {
      if (child.isClient()) {
        collectionRepository.updateGalleryPassword(child.getId(), request.password());
        log.info(
            "Propagated parent (id={}) gallery password to client child (id={}, slug={})",
            parent.getId(),
            child.getId(),
            child.getSlug());
      }
    }
  }
}
