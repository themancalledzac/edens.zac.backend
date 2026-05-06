package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AdminHomeTileRepository;
import edens.zac.portfolio.backend.dao.AdminHomeTileRepository.TileRow;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves admin home tile configuration for the dev console. Cover URLs are picked at random per
 * strategy and cached in-process; the existing admin Clear Cache action calls {@link #evictAll()}
 * to force a fresh pick.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminHomeService {

  private final AdminHomeTileRepository tileRepository;
  private final CollectionService collectionService;
  private final ContentRepository contentRepository;

  private final AtomicReference<List<Records.AdminHomeTileResponse>> cache =
      new AtomicReference<>();

  /**
   * Return the ordered list of admin home tiles with cover URLs resolved per tile-key strategy.
   * Result is cached in-process; call {@link #evictAll()} to force a recompute.
   */
  @Transactional(readOnly = true)
  public List<Records.AdminHomeTileResponse> getTiles() {
    List<Records.AdminHomeTileResponse> cached = cache.get();
    if (cached != null) {
      return cached;
    }
    List<Records.AdminHomeTileResponse> resolved = resolveAll();
    cache.set(resolved);
    return resolved;
  }

  /** Drop the cached tile list so the next call recomputes random selections. */
  public void evictAll() {
    cache.set(null);
  }

  private List<Records.AdminHomeTileResponse> resolveAll() {
    List<TileRow> rows = tileRepository.findAllOrderedByDisplay();
    List<Records.AdminHomeTileResponse> out = new ArrayList<>(rows.size());
    for (TileRow row : rows) {
      String url = resolveCover(row.tileKey());
      out.add(new Records.AdminHomeTileResponse(row.tileKey(), url, row.displayOrder()));
    }
    return List.copyOf(out);
  }

  private String resolveCover(String tileKey) {
    return switch (tileKey) {
      case "home" -> randomCoverUrlFromCollections(collectionService.findChildCollectionsForHome());
      case "all-collections" ->
          randomCoverUrlFromCollections(collectionService.findAllListedWithCovers());
      case "all-images" -> contentRepository.findRandomImageWebUrl().orElse(null);
      case "blogs" ->
          randomCoverUrlFromCollections(
              collectionService.findVisibleByTypeOrderByDate(CollectionType.BLOG));
      case "client-galleries" ->
          randomCoverUrlFromCollections(
              collectionService.findVisibleByTypeOrderByDate(CollectionType.CLIENT_GALLERY));
      // metadata, comments, create, manage, about: no cover (rendered as plain entries elsewhere)
      default -> null;
    };
  }

  private String randomCoverUrlFromCollections(List<CollectionModel> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    List<String> urls =
        candidates.stream()
            .map(CollectionModel::getCoverImage)
            .filter(Objects::nonNull)
            .map(ContentModels.Image::imageUrl)
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .toList();
    if (urls.isEmpty()) {
      return null;
    }
    return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
  }
}
