package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionUpdateDTO;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
