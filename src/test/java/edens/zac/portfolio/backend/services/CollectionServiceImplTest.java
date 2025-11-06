package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.repository.CollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionProcessingUtil collectionProcessingUtil;
    @Mock private ContentService contentService;

    @InjectMocks
    private CollectionServiceImpl service;

    @BeforeEach
    void setup() {
        // Nothing required; @InjectMocks wires constructor
    }

   // TODO: update to include relevant tests
}
