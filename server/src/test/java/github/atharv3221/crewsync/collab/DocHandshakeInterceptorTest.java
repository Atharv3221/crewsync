package github.atharv3221.crewsync.collab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import github.atharv3221.crewsync.auth.JwtService;
import github.atharv3221.crewsync.document.DocMemberId;
import github.atharv3221.crewsync.document.DocMemberRepository;
import github.atharv3221.crewsync.document.DocumentRepository;
import github.atharv3221.crewsync.user.TestUsers;
import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

class DocHandshakeInterceptorTest {

  private final JwtService jwtService = mock(JwtService.class);
  private final DocumentRepository documentRepository = mock(DocumentRepository.class);
  private final DocMemberRepository memberRepository = mock(DocMemberRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final DocHandshakeInterceptor interceptor = new DocHandshakeInterceptor(
      jwtService, documentRepository, memberRepository, userRepository);
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final Map<String, Object> attributes = new HashMap<>();
  private final UUID docId = UUID.randomUUID();
  private final User user = TestUsers.user("Ada");
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(user, "id", userId);
    when(jwtService.findUserId("good")).thenReturn(Optional.of(userId));
    when(jwtService.findUserId("bad")).thenReturn(Optional.empty());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(documentRepository.existsById(docId)).thenReturn(true);
  }

  @Test
  void acceptsAMemberWithAValidToken() {
    when(memberRepository.existsById(new DocMemberId(docId, userId))).thenReturn(true);

    assertThat(handshake("/ws/" + docId, "good")).isTrue();
    assertThat(attributes)
        .containsEntry(DocHandshakeInterceptor.DOC_ID_ATTRIBUTE, docId)
        .containsEntry(DocHandshakeInterceptor.USER_ATTRIBUTE, new SessionUser(userId, "Ada"));
  }

  @Test
  void rejectsMissingTokenWith401() {
    assertThat(handshake("/ws/" + docId, null)).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(attributes).isEmpty();
  }

  @Test
  void rejectsInvalidTokenWith401() {
    assertThat(handshake("/ws/" + docId, "bad")).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void rejectsTokenOfDeletedUserWith401() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThat(handshake("/ws/" + docId, "good")).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void rejectsNonMemberWith403() {
    assertThat(handshake("/ws/" + docId, "good")).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(attributes).isEmpty();
  }

  @Test
  void rejectsUnknownDocumentWith404() {
    assertThat(handshake("/ws/" + UUID.randomUUID(), "good")).isFalse();
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void rejectsInvalidDocIdWith400() {
    assertThat(handshake("/ws/abc123", "good")).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  private boolean handshake(String path, String token) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    if (token != null) {
      request.setQueryString("token=" + token);
    }
    return interceptor.beforeHandshake(new ServletServerHttpRequest(request),
        new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);
  }
}
