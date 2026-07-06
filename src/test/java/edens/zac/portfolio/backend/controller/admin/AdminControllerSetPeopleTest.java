package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.services.AdminHomeService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.ImageUploadPipelineService;
import edens.zac.portfolio.backend.services.JobTrackingService;
import edens.zac.portfolio.backend.services.MetadataService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminControllerSetPeopleTest {

  @Mock private AdminHomeService adminHomeService;
  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ImageUploadPipelineService imageUploadPipelineService;
  @Mock private JobTrackingService jobTrackingService;
  @Mock private MetadataService metadataService;

  @InjectMocks private AdminController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void setPeopleDelegatesToCollectionService() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/collections/42/people")
                .contentType("application/json")
                .content("[1,2]"))
        .andExpect(status().isNoContent());

    verify(collectionService).setCollectionPeople(eq(42L), eq(List.of(1L, 2L)));
  }
}
