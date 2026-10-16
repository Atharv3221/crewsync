package github.atharv3221.crewsync.collab;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class TestSessions {

  static final JsonMapper JSON = JsonMapper.builder().build();

  private TestSessions() {
  }

  static WebSocketSession openSession(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  /** An open session authenticated as {@code userId}, as the handshake would leave it. */
  static WebSocketSession openSession(String id, UUID userId) {
    WebSocketSession session = openSession(id);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(DocHandshakeInterceptor.USER_ATTRIBUTE, new SessionUser(userId, id));
    when(session.getAttributes()).thenReturn(attributes);
    return session;
  }

  static List<JsonNode> received(WebSocketSession session) {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    try {
      verify(session, atLeast(0)).sendMessage(captor.capture());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return captor.getAllValues().stream().map(message -> JSON.readTree(message.getPayload())).toList();
  }

  /** Batching that never waits: the writer stores whatever is queued right away. */
  static final DocRoom.Batching NO_WAIT = new DocRoom.Batching(100, Duration.ZERO);

  /** A room whose writer runs inline on the submitting thread, so tests can assert right away. */
  static DocRoom room(UUID docId, OpLog opLog) {
    return new DocRoom(docId, opLog, new MessageCodec(JSON), NO_WAIT, Runnable::run, room -> {
    });
  }

  static ClientOp insert(long counter, String site) {
    return ClientOp.parse(JSON.readTree("""
        {"kind":"insert","counter":%d,"site":"%s","value":"x","origin":null}
        """.formatted(counter, site)));
  }
}
