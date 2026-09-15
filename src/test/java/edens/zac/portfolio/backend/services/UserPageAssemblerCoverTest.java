package edens.zac.portfolio.backend.services;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for {@link UserPageAssembler#resolveCover(Long)}: the {@code /user} page cover must
 * be looked up with the current UTC day as its seed, so repeated requests within the same day do
 * not re-roll it.
 */
@ExtendWith(MockitoExtension.class)
class UserPageAssemblerCoverTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private PersonRepository personRepository;
  @Mock private CollectionAccessService collectionAccessService;
  @Mock private ShareLinkService shareLinkService;
  @Mock private CollectionRepository collectionRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private ContentModelConverter contentModelConverter;

  @InjectMocks private UserPageAssembler assembler;

  @Test
  void coverIsChosenWithTheDaySeedSoARequestDoesNotReRollIt() {
    ContentPersonEntity person =
        ContentPersonEntity.builder().id(7L).personName("Cover Person").build();
    when(personRepository.findById(7L)).thenReturn(Optional.of(person));
    when(collectionRepository.findCollectionIdsByPersonId(7L)).thenReturn(List.of(100L));
    when(contentRepository.findCoverImageIdByPersonId(eq(7L), anyString()))
        .thenReturn(Optional.empty());

    assembler.assembleForUser(7L);

    String today = String.valueOf(LocalDate.now(ZoneOffset.UTC).toEpochDay());
    verify(contentRepository).findCoverImageIdByPersonId(7L, today);
  }
}
