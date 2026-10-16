package github.atharv3221.crewsync.user;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.document.DocRole;
import github.atharv3221.crewsync.document.DocumentDtos.MemberDocumentResponse;
import github.atharv3221.crewsync.document.DocumentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  private static final UUID USER_ID = UUID.fromString("7a1c2d4e-0000-4000-8000-000000000001");
  private static final UUID DOC_ID = UUID.fromString("7a1c2d4e-0000-4000-8000-000000000002");

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private DocumentService documentService;

  @Test
  void createReturns201WithUser() throws Exception {
    given(userService.create("Ada")).willReturn(user("Ada"));

    mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Ada\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/users/" + USER_ID))
        .andExpect(jsonPath("$.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.name").value("Ada"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void createWithBlankNameReturns400() throws Exception {
    given(userService.create(" ")).willThrow(new IllegalArgumentException("Name must not be blank"));

    mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Name must not be blank"));
  }

  @Test
  void getReturnsUser() throws Exception {
    given(userService.get(USER_ID)).willReturn(user("Ada"));

    mockMvc.perform(get("/api/users/{id}", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada"));
  }

  @Test
  void getUnknownUserReturns404() throws Exception {
    given(userService.get(USER_ID)).willThrow(new NotFoundException("User", USER_ID));

    mockMvc.perform(get("/api/users/{id}", USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void renameReturnsUpdatedUser() throws Exception {
    given(userService.rename(USER_ID, "Grace")).willReturn(user("Grace"));

    mockMvc.perform(patch("/api/users/{id}", USER_ID).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Grace"));
  }

  @Test
  void listsDocumentsWithRole() throws Exception {
    Instant now = Instant.parse("2026-10-16T10:15:30Z");
    given(documentService.documentsOf(USER_ID)).willReturn(
        List.of(new MemberDocumentResponse(DOC_ID, "Plans", now, DocRole.EDITOR, now)));

    mockMvc.perform(get("/api/users/{id}/docs", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(DOC_ID.toString()))
        .andExpect(jsonPath("$[0].title").value("Plans"))
        .andExpect(jsonPath("$[0].role").value("EDITOR"))
        .andExpect(jsonPath("$[0].createdAt").value("2026-10-16T10:15:30Z"));
  }

  @Test
  void listingDocumentsOfUnknownUserReturns404() throws Exception {
    given(documentService.documentsOf(USER_ID)).willThrow(new NotFoundException("User", USER_ID));

    mockMvc.perform(get("/api/users/{id}/docs", USER_ID))
        .andExpect(status().isNotFound());
  }

  @Test
  void listingDocumentsWithInvalidIdReturns400() throws Exception {
    mockMvc.perform(get("/api/users/{id}/docs", "123"))
        .andExpect(status().isBadRequest());
  }

  private static User user(String name) {
    User user = new User(name);
    ReflectionTestUtils.setField(user, "id", USER_ID);
    return user;
  }
}
