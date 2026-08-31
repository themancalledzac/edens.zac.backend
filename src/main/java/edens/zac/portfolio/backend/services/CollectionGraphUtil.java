package edens.zac.portfolio.backend.services;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Breadth-first walk over the collection link graph, shared by {@link CollectionService} and {@link
 * RoleGrantPropagationService}. Both directions are just a different neighbour lookup: parents for
 * an ancestor walk, children for a subtree walk. The visited set is what makes every caller
 * cycle-safe, so a cycle already stored cannot make a walk loop forever.
 */
final class CollectionGraphUtil {

  private CollectionGraphUtil() {}

  /**
   * Walk outward from {@code root}, following {@code neighbors} and calling {@code visitor} once
   * per distinct collection reached. The root is pre-marked visited, so it is never passed to the
   * visitor even when the graph loops back to it. Returns the root followed by everything reached,
   * in the order it was reached.
   */
  static Set<Long> walk(
      Long root, Function<Long, Collection<Long>> neighbors, Consumer<Long> visitor) {
    Set<Long> visited = new LinkedHashSet<>();
    visited.add(root);
    Deque<Long> pending = new ArrayDeque<>(neighbors.apply(root));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      visitor.accept(current);
      pending.addAll(neighbors.apply(current));
    }
    return visited;
  }

  /** {@link #walk} with no per-collection work, for callers that only want the reachable set. */
  static Set<Long> walk(Long root, Function<Long, Collection<Long>> neighbors) {
    return walk(root, neighbors, id -> {});
  }
}
