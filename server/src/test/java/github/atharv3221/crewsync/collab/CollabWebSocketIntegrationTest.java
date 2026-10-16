package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.atharv3221.crewsync.document.DocumentService;
import github.atharv3221.crewsync.user.UserService;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CollabWebSocketIntegrationTest {

  private static final String INSERT = "{\"type\":\"op\",\"op\":{\"kind\":\"insert\","
      + "\"counter\":1,\"site\":\"s1\",\"value\":\"h\",\"origin\":null}}";

  @LocalServerPort
  private int port;

  @Autowired
  private UserService userService;

  @Autowired
  private DocumentService documentService;

  private UUID docId;
  private Client first;
  private Client second;

  @BeforeEach
  void setUp() {
    UUID creatorId = userService.create("Ada").getId();
    docId = documentService.create("Notes", creatorId).getId();
  }

  @AfterEach
  void tearDown() throws Exception {
    for (Client client : new Client[] {first, second}) {
      if (client != null) {
        client.session.close();
      }
    }
  }

  @Test
  void opsAreSequencedPersistedAndReplayed() throws Exception {
    first = connect(docId);
    second = connect(docId);
    first.send("{\"type\":\"hello\",\"site\":\"s1\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"site\":\"s2\",\"lastSeq\":0}");
    assertThat(first.next().get("type").stringValue()).isEqualTo("init");
    assertThat(second.next().get("ops")).isEmpty();

    first.send(INSERT);
    first.send(INSERT);

    JsonNode echo = first.next();
    assertThat(echo.get("seq").longValue()).isEqualTo(1);
    assertThat(second.next()).isEqualTo(echo);
    assertThat(first.poll()).isNull();

    Client late = connect(docId);
    late.send("{\"type\":\"hello\",\"site\":\"s3\",\"lastSeq\":0}");
    JsonNode init = late.next();
    assertThat(init.get("seq").longValue()).isEqualTo(1);
    assertThat(init.get("ops").get(0).get("op")).isEqualTo(echo.get("op"));
    late.session.close();
  }

  @Test
  void presenceIsRelayedToOthers() throws Exception {
    first = connect(docId);
    second = connect(docId);
    first.send("{\"type\":\"hello\",\"site\":\"s1\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"site\":\"s2\",\"lastSeq\":0}");
    first.next();
    second.next();

    first.send("{\"type\":\"presence\",\"site\":\"s1\",\"name\":\"Ada\",\"cursor\":null}");

    assertThat(second.next().get("name").stringValue()).isEqualTo("Ada");
    assertThat(first.poll()).isNull();
  }

  @Test
  void malformedMessageClosesTheSession() throws Exception {
    first = connect(docId);

    first.send("{\"type\":\"op\",\"op\":{\"kind\":\"insert\"}}");

    assertThat(first.closed.poll(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void handshakeIsRejectedForUnknownOrInvalidDocs() {
    assertThatThrownBy(() -> connect(UUID.randomUUID())).isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect("not-a-uuid")).isInstanceOf(ExecutionException.class);
  }

  private Client connect(Object docId) throws Exception {
    Client client = new Client();
    client.session = new StandardWebSocketClient()
        .execute(client, "ws://localhost:" + port + "/ws/" + docId)
        .get(5, TimeUnit.SECONDS);
    return client;
  }

  private static final class Client extends TextWebSocketHandler {

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final BlockingQueue<Boolean> closed = new LinkedBlockingQueue<>();
    private WebSocketSession session;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
      messages.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
        CloseStatus status) {
      closed.add(true);
    }

    void send(String payload) throws Exception {
      session.sendMessage(new TextMessage(payload));
    }

    JsonNode next() throws InterruptedException {
      String payload = messages.poll(5, TimeUnit.SECONDS);
      assertThat(payload).as("expected a message").isNotNull();
      return JSON.readTree(payload);
    }

    String poll() throws InterruptedException {
      return messages.poll(300, TimeUnit.MILLISECONDS);
    }
  }
}
