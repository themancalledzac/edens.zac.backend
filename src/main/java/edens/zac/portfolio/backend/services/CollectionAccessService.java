package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-collection access, resolved through role membership. A user may VIEW a collection when any of
 * their roles grants it (any level); CLIENT powers (download/tag/star) require a CLIENT grant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionAccessService {

  private final RoleRepository roleRepository;

  /** True when the user may VIEW the collection through any of their roles. */
  @Transactional(readOnly = true)
  public boolean canView(Long userId, Long collectionId) {
    return roleRepository.canView(userId, collectionId);
  }

  /** True when the user may DOWNLOAD / TAG (a CLIENT grant on any of their roles). */
  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    return roleRepository.isClient(userId, collectionId);
  }

  /** Distinct collection ids the user can reach through any role — for the /user listing. */
  @Transactional(readOnly = true)
  public List<Long> memberCollectionIdsForUser(Long userId) {
    return roleRepository.memberCollectionIdsForUser(userId);
  }

  /** Deduped (collectionId, level) the user can reach, CLIENT winning on conflict. */
  @Transactional(readOnly = true)
  public List<EffectiveGrant> effectiveGrants(Long userId) {
    return roleRepository.effectiveGrants(userId);
  }
}
