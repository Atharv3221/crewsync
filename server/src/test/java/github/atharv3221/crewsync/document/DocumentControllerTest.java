package github.atharv3221.crewsync.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

  private static final UUID DOC_ID = UUID.fromString("3f1c2d4e-0000-4000-8000-000000000001");
  private static final UUID CREATOR_ID = UUID.fromString("3f1c2d4e-0000-4000-8000-000000000002");

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DocumentService documentService;

  @Test
  void createReturns201WithDocument() throws Exception {
    given(documentService.create("Plans", CREATOR_ID)).willReturn(document("Plans"));

    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Plans\",\"creatorId\":\"" + CREATOR_ID + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/docs/" + DOC_ID))
        .andExpect(jsonPath("$.id").value(DOC_ID.toString()))
        .andExpect(jsonPath("$.title").value("Plans"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.createdBy").doesNotExist());
  }

  @Test
  void createWithoutTitleIsAllowed() throws Exception {
    given(documentService.create(null, CREATOR_ID)).willReturn(document(Document.DEFAULT_TITLE));

    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON)
            .content("{\"creatorId\":\"" + CREATOR_ID + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value(Document.DEFAULT_TITLE));
  }

  @Test
  void createWithUnknownCreatorReturns404() throws Exception {
    given(documentService.create(any(), eq(CREATOR_ID)))
        .willThrow(new NotFoundException("User", CREATOR_ID));

    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Plans\",\"creatorId\":\"" + CREATOR_ID + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("User " + CREATOR_ID + " not found"));
  }

  @Test
  void createWithInvalidInputReturns400() throws Exception {
    given(documentService.create(any(), any()))
        .willThrow(new IllegalArgumentException("creatorId is required"));

    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Plans\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("creatorId is required"));
  }

  @Test
  void createWithMalformedBodyReturns400() throws Exception {
    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON)
            .content("{\"creatorId\":\"not-a-uuid\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void getReturnsDocument() throws Exception {
    given(documentService.get(DOC_ID)).willReturn(document("Plans"));

    mockMvc.perform(get("/api/docs/{id}", DOC_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(DOC_ID.toString()))
        .andExpect(jsonPath("$.title").value("Plans"));
  }

  @Test
  void getUnknownDocumentReturns404() throws Exception {
    given(documentService.get(DOC_ID)).willThrow(new NotFoundException("Document", DOC_ID));

    mockMvc.perform(get("/api/docs/{id}", DOC_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not Found"));
  }

  @Test
  void getWithInvalidIdReturns400() throws Exception {
    mockMvc.perform(get("/api/docs/{id}", "nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
  }

  private static Document document(String title) {
    User creator = new User("Ada");
    ReflectionTestUtils.setField(creator, "id", CREATOR_ID);
    Document document = new Document(title, creator);
    ReflectionTestUtils.setField(document, "id", DOC_ID);
    return document;
  }
}
