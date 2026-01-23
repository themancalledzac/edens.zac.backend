package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionDao collectionDao;
    @Mock private CollectionContentDao collectionContentDao;
    @Mock private ContentDao contentDao;
    @Mock private ContentCollectionDao contentCollectionDao;
    @Mock private ContentTextDao contentTextDao;
    @Mock private CollectionProcessingUtil collectionProcessingUtil;
    @Mock private ContentProcessingUtil contentProcessingUtil;
    @Mock private ContentService contentService;

    @InjectMocks
    private CollectionServiceImpl service;

    @BeforeEach
    void setup() {
        // Nothing required; @InjectMocks wires constructor
    }

   // TODO: update to include relevant tests
}
