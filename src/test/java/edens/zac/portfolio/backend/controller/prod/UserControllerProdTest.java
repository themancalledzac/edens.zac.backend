package edens.zac.portfolio.backend.controller.prod;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.Role;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerProdTest {

  @Mock private UserPageAssembler userPageAssembler;

  @InjectMocks private UserControllerProd controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor asUser(AuthPrincipal principal) {
    return request -> {
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
      return request;
    };
  }

  @Test
  void anonymousRequestIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/read/user/me/page")).andExpect(status().isUnauthorized());

    verify(userPageAssembler, never()).assembleForUser(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void authenticatedRequestReturnsAssembledPage() throws Exception {
    AuthPrincipal user = new AuthPrincipal(7L, "c@example.com", Role.CLIENT, true);
    CollectionModel assembled =
        CollectionModel.builder()
            .slug("user")
            .title("Jane Doe")
            .type(CollectionType.PARENT)
            .visibility(CollectionVisibility.UNLISTED)
            .contentCount(0)
            .contentPerPage(0)
            .currentPage(0)
            .totalPages(1)
            .content(List.of())
            .build();
    when(userPageAssembler.assembleForUser(eq(7L))).thenReturn(assembled);

    mockMvc
        .perform(get("/api/read/user/me/page").with(asUser(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug", org.hamcrest.Matchers.is("user")))
        .andExpect(jsonPath("$.title", org.hamcrest.Matchers.is("Jane Doe")))
        .andExpect(jsonPath("$.type", org.hamcrest.Matchers.is("PARENT")));

    verify(userPageAssembler).assembleForUser(7L);
  }
}
