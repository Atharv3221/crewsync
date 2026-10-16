package github.atharv3221.crewsync.collab;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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

  static List<JsonNode> received(WebSocketSession session) {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    try {
      verify(session, atLeast(0)).sendMessage(captor.capture());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return captor.getAllValues().stream().map(message -> JSON.readTree(message.getPayload())).toList();
  }

  static ClientOp insert(long counter, String site) {
    return ClientOp.parse(JSON.readTree("""
        {"kind":"insert","counter":%d,"site":"%s","value":"x","origin":null}
        """.formatted(counter, site)));
  }
}
