package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-collection access reads, backed by user_collection membership. GENERAL or CLIENT membership
 * grants viewing; CLIENT additionally grants download/tag/star.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCollectionService {

  private final UserCollectionRepository userCollectionRepository;

  /** True when the user may VIEW the collection (any membership). */
  @Transactional(readOnly = true)
  public boolean canView(Long userId, Long collectionId) {
    return userCollectionRepository.hasMembership(userId, collectionId);
  }

  /** True when the user may DOWNLOAD / TAG (CLIENT membership). */
  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    return userCollectionRepository.hasClientMembership(userId, collectionId);
  }

  /** Collection ids the user is a member of (any role) — for the /user page listing. */
  @Transactional(readOnly = true)
  public List<Long> memberCollectionIdsForUser(Long userId) {
    return userCollectionRepository.findCollectionIdsByUserId(userId);
  }
}
