package github.atharv3221.crewsync.collab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import github.atharv3221.crewsync.document.DocumentRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class DocHandshakeInterceptorTest {

  private final DocumentRepository documentRepository = mock(DocumentRepository.class);
  private final DocHandshakeInterceptor interceptor = new DocHandshakeInterceptor(documentRepository);
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final Map<String, Object> attributes = new HashMap<>();

  @Test
  void acceptsExistingDocument() {
    UUID docId = UUID.randomUUID();
    when(documentRepository.existsById(docId)).thenReturn(true);

    assertThat(handshake("/ws/" + docId)).isTrue();
    assertThat(attributes).containsEntry(DocHandshakeInterceptor.DOC_ID_ATTRIBUTE, docId);
  }

  @Test
  void rejectsUnknownDocumentWith404() {
    assertThat(handshake("/ws/" + UUID.randomUUID())).isFalse();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(attributes).isEmpty();
  }

  @Test
  void rejectsInvalidDocIdWith400() {
    assertThat(handshake("/ws/abc123")).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  private boolean handshake(String path) {
    return interceptor.beforeHandshake(
        new ServletServerHttpRequest(new MockHttpServletRequest("GET", path)),
        new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);
  }
}
