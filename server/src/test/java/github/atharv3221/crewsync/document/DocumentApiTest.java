package github.atharv3221.crewsync.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import github.atharv3221.crewsync.auth.AuthDtos.AuthResponse;
import github.atharv3221.crewsync.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentApiTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuthService authService;

  @Autowired
  private DocumentService documentService;

  private AuthResponse owner;
  private AuthResponse editor;
  private AuthResponse outsider;
  private UUID docId;

  @BeforeEach
  void setUp() {
    owner = authService.signup("Ada", "ada@example.com", "password1");
    editor = authService.signup("Grace", "grace@example.com", "password1");
    outsider = authService.signup("Eve", "eve@example.com", "password1");
    docId = documentService.create("Plans", owner.user().id()).id();
    documentService.invite(docId, owner.user().id(), "grace@example.com");
  }

  @Test
  void requestsWithoutATokenGet401Json() throws Exception {
    mockMvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.message").value("Authentication required"));
    mockMvc.perform(get("/api/docs/{id}", docId)).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/docs").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void badTokensGet401() throws Exception {
    for (String header : new String[] {"Bearer not-a-jwt", "Bearer " + owner.token() + "x",
        "Basic " + owner.token()}) {
      mockMvc.perform(get("/api/me").header("Authorization", header))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void oldUserEndpointsAreGone() throws Exception {
    mockMvc.perform(get("/api/users/{id}", owner.user().id()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(as(owner, get("/api/users/{id}", owner.user().id())))
        .andExpect(status().isNotFound());
    mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Guest\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsAndRenamesTheCaller() throws Exception {
    mockMvc.perform(as(editor, get("/api/me")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(editor.user().id().toString()))
        .andExpect(jsonPath("$.email").value("grace@example.com"));
    mockMvc.perform(as(editor, patch("/api/me")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  Grace  Hopper \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Grace Hopper"));
  }

  @Test
  void myDocsListsMembershipsWithRoles() throws Exception {
    mockMvc.perform(as(editor, get("/api/me/docs")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(docId.toString()))
        .andExpect(jsonPath("$[0].title").value("Plans"))
        .andExpect(jsonPath("$[0].role").value("EDITOR"))
        .andExpect(jsonPath("$[0].createdAt").exists());
    mockMvc.perform(as(outsider, get("/api/me/docs")))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void createdDocIsOwnedByTheTokenUserNotTheBody() throws Exception {
    String body = "{\"title\":\"Mine\",\"creatorId\":\"" + owner.user().id() + "\"}";

    String location = mockMvc.perform(as(outsider, post("/api/docs"))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.title").value("Mine"))
        .andExpect(jsonPath("$.role").value("OWNER"))
        .andReturn().getResponse().getHeader("Location");

    mockMvc.perform(as(outsider, get(location))).andExpect(status().isOk());
    mockMvc.perform(as(owner, get(location))).andExpect(status().isForbidden());
  }

  @Test
  void docAccessDependsOnMembership() throws Exception {
    mockMvc.perform(as(editor, get("/api/docs/{id}", docId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("EDITOR"));
    mockMvc.perform(as(outsider, get("/api/docs/{id}", docId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"));
    mockMvc.perform(as(outsider, get("/api/docs/{id}", UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerRenamesWithPatch() throws Exception {
    mockMvc.perform(as(owner, patch("/api/docs/{id}", docId))
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Launch  plan \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Launch plan"))
        .andExpect(jsonPath("$.titleVersion").value(1))
        .andExpect(jsonPath("$.role").value("OWNER"));
    mockMvc.perform(as(editor, get("/api/me/docs")))
        .andExpect(jsonPath("$[0].title").value("Launch plan"));
  }

  @Test
  void editorsGet403WhenRenaming() throws Exception {
    mockMvc.perform(as(editor, patch("/api/docs/{id}", docId))
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mine now\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Only the owner can rename this document"));
    mockMvc.perform(as(owner, get("/api/docs/{id}", docId)))
        .andExpect(jsonPath("$.titleVersion").value(0));
  }

  @Test
  void renameIsRefusedForNonMembersAndOverlongTitles() throws Exception {
    mockMvc.perform(as(outsider, patch("/api/docs/{id}", docId))
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mine\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(as(owner, patch("/api/docs/{id}", docId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"" + "t".repeat(201) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Title must be at most 200 characters"));
    mockMvc.perform(patch("/api/docs/{id}", docId)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Anon\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void membersAreVisibleToMembersOnly() throws Exception {
    mockMvc.perform(as(editor, get("/api/docs/{id}/members", docId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(owner.user().id().toString()))
        .andExpect(jsonPath("$[0].email").value("ada@example.com"))
        .andExpect(jsonPath("$[0].role").value("OWNER"))
        .andExpect(jsonPath("$[1].name").value("Grace"))
        .andExpect(jsonPath("$[1].role").value("EDITOR"));
    mockMvc.perform(as(outsider, get("/api/docs/{id}/members", docId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerInvitesByEmail() throws Exception {
    mockMvc.perform(invite(owner, "EVE@example.com"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(outsider.user().id().toString()))
        .andExpect(jsonPath("$.role").value("EDITOR"));
    mockMvc.perform(as(outsider, get("/api/docs/{id}", docId))).andExpect(status().isOk());
  }

  @Test
  void nonOwnerInviteIsForbidden() throws Exception {
    mockMvc.perform(invite(editor, "eve@example.com"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Only the owner can manage members"));
    mockMvc.perform(invite(outsider, "eve@example.com")).andExpect(status().isForbidden());
  }

  @Test
  void invitingUnknownEmailIs404() throws Exception {
    mockMvc.perform(invite(owner, "nobody@example.com"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("No user with that email"));
  }

  @Test
  void invitingAnExistingMemberIs409() throws Exception {
    mockMvc.perform(invite(owner, "grace@example.com"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void ownerRemovesAnEditorButNotThemselves() throws Exception {
    mockMvc.perform(as(owner, delete("/api/docs/{id}/members/{userId}", docId, owner.user().id())))
        .andExpect(status().isBadRequest());
    mockMvc.perform(as(editor, delete("/api/docs/{id}/members/{userId}", docId, editor.user().id())))
        .andExpect(status().isForbidden());

    mockMvc.perform(as(owner, delete("/api/docs/{id}/members/{userId}", docId, editor.user().id())))
        .andExpect(status().isNoContent());
    mockMvc.perform(as(editor, get("/api/docs/{id}", docId))).andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder invite(AuthResponse caller, String email) {
    return as(caller, post("/api/docs/{id}/members", docId))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}");
  }

  private static MockHttpServletRequestBuilder as(AuthResponse caller,
      MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + caller.token());
  }
}
