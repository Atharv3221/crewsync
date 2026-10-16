package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.atharv3221.crewsync.auth.AuthDtos.AuthResponse;
import github.atharv3221.crewsync.auth.AuthService;
import github.atharv3221.crewsync.document.DocumentService;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  private AuthService authService;

  @Autowired
  private DocumentService documentService;

  private AuthResponse owner;
  private AuthResponse editor;
  private UUID docId;
  private Client first;
  private Client second;

  @BeforeEach
  void setUp() {
    owner = authService.signup("Ada", uniqueEmail(), "password1");
    editor = authService.signup("Grace", uniqueEmail(), "password1");
    docId = documentService.create("Notes", owner.user().id()).id();
    documentService.invite(docId, owner.user().id(), editor.user().email());
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
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"site\":\"s1\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"site\":\"s2\",\"lastSeq\":0}");
    assertThat(first.next().get("type").stringValue()).isEqualTo("init");
    assertThat(second.next().get("ops")).isEmpty();

    first.send(INSERT);
    first.send(INSERT);

    JsonNode echo = first.next();
    assertThat(echo.get("seq").longValue()).isEqualTo(1);
    assertThat(second.next()).isEqualTo(echo);
    assertThat(first.next()).isEqualTo(echo);
    assertThat(second.poll()).isNull();

    Client late = connect(docId, editor);
    late.send("{\"type\":\"hello\",\"site\":\"s3\",\"lastSeq\":0}");
    JsonNode init = late.next();
    assertThat(init.get("seq").longValue()).isEqualTo(1);
    assertThat(init.get("ops").get(0).get("op")).isEqualTo(echo.get("op"));
    late.session.close();
  }

  @Test
  void presenceIsRelayedToOthersWithTheAuthenticatedIdentity() throws Exception {
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"site\":\"s1\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"site\":\"s2\",\"lastSeq\":0}");
    first.next();
    second.next();

    first.send("{\"type\":\"presence\",\"site\":\"s1\",\"userId\":\""
        + editor.user().id() + "\",\"name\":\"Grace\",\"cursor\":null}");

    JsonNode relayed = second.next();
    assertThat(relayed.get("userId").stringValue()).isEqualTo(owner.user().id().toString());
    assertThat(relayed.get("name").stringValue()).isEqualTo("Ada");
    assertThat(first.poll()).isNull();
  }

  @Test
  void removingAMemberClosesTheirSessionsWith4403AndDropsTheirPresence() throws Exception {
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"lastSeq\":0}");
    first.next();
    second.next();

    documentService.removeMember(docId, owner.user().id(), editor.user().id());

    CloseStatus status = second.closed.poll(5, TimeUnit.SECONDS);
    assertThat(status).isNotNull();
    assertThat(status.getCode()).isEqualTo(4403);
    JsonNode leave = first.next();
    assertThat(leave.get("type").stringValue()).isEqualTo("leave");
    assertThat(leave.get("userId").stringValue()).isEqualTo(editor.user().id().toString());
    assertThatThrownBy(() -> connect(docId, editor)).isInstanceOf(ExecutionException.class);

    first.send(INSERT);
    assertThat(first.next().get("seq").longValue()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\\n", " ", "\\t"})
  void whitespaceCharactersAreAcceptedAsInsertValues(String escapedValue) throws Exception {
    first = connect(docId, owner);
    first.send("{\"type\":\"hello\",\"lastSeq\":0}");
    first.next();

    first.send("{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":1,"
        + "\"site\":\"s1\",\"value\":\"" + escapedValue + "\",\"origin\":null}}");

    JsonNode echo = first.next();
    assertThat(echo.get("seq").longValue()).isEqualTo(1);
    assertThat(echo.get("op").get("value").stringValue())
        .isEqualTo(JSON.readTree("\"" + escapedValue + "\"").stringValue());
    assertThat(first.closed).isEmpty();
  }

  @Test
  void renamingTheDocTellsEveryoneInTheRoom() throws Exception {
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"lastSeq\":0}");
    first.next();
    second.next();

    documentService.rename(docId, owner.user().id(), "Launch plan");

    for (Client client : new Client[] {first, second}) {
      JsonNode message = client.next();
      assertThat(message.get("type").stringValue()).isEqualTo("title");
      assertThat(message.get("title").stringValue()).isEqualTo("Launch plan");
    }
  }

  @Test
  void ownerRenamesOverHttpReachEveryoneAndEditorRenamesAreRefused() throws Exception {
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"lastSeq\":0}");
    first.next();
    second.next();

    assertThat(patchTitle(owner, "Owner's name").statusCode()).isEqualTo(200);
    assertThat(first.next().get("title").stringValue()).isEqualTo("Owner's name");
    assertThat(second.next().get("title").stringValue()).isEqualTo("Owner's name");

    assertThat(patchTitle(editor, "Editor's name").statusCode()).isEqualTo(403);
    assertThat(first.poll()).isNull();
    assertThat(second.poll()).isNull();

    assertThat(documentService.get(docId, owner.user().id()).title()).isEqualTo("Owner's name");
    assertThat(documentService.get(docId, editor.user().id()).title()).isEqualTo("Owner's name");
  }

  @Test
  void simultaneousRenamesLeaveEveryoneOnTheSavedTitle() throws Exception {
    first = connect(docId, owner);
    second = connect(docId, editor);
    first.send("{\"type\":\"hello\",\"lastSeq\":0}");
    second.send("{\"type\":\"hello\",\"lastSeq\":0}");
    first.next();
    second.next();

    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      for (int round = 0; round < 10; round++) {
        String suffix = " " + round;
        // Two of the owner's tabs saving at the same moment.
        var a = pool.submit(() -> patchTitle(owner, "Tab A" + suffix));
        var b = pool.submit(() -> patchTitle(owner, "Tab B" + suffix));
        assertThat(a.get().statusCode()).isEqualTo(200);
        assertThat(b.get().statusCode()).isEqualTo(200);
      }
    }

    var saved = documentService.get(docId, owner.user().id());
    assertThat(saved.titleVersion()).isEqualTo(20);
    for (Client client : new Client[] {first, second}) {
      JsonNode last = null;
      long previous = 0;
      for (String raw = client.poll(); raw != null; raw = client.poll()) {
        JsonNode message = JSON.readTree(raw);
        assertThat(message.get("version").longValue()).isGreaterThan(previous);
        previous = message.get("version").longValue();
        last = message;
      }
      assertThat(last).isNotNull();
      assertThat(last.get("title").stringValue()).isEqualTo(saved.title());
      assertThat(last.get("version").longValue()).isEqualTo(20);
    }
  }

  private java.net.http.HttpResponse<String> patchTitle(AuthResponse as, String title)
      throws Exception {
    var request = java.net.http.HttpRequest.newBuilder(
            java.net.URI.create("http://localhost:" + port + "/api/docs/" + docId))
        .header("Authorization", "Bearer " + as.token())
        .header("Content-Type", "application/json")
        .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(
            "{\"title\":\"" + title + "\"}"))
        .build();
    try (var http = java.net.http.HttpClient.newHttpClient()) {
      return http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  void malformedMessageClosesTheSession() throws Exception {
    first = connect(docId, owner);

    first.send("{\"type\":\"op\",\"op\":{\"kind\":\"insert\"}}");

    assertThat(first.closed.poll(5, TimeUnit.SECONDS)).isNotNull();
  }

  @Test
  void handshakeIsRejectedForUnknownOrInvalidDocs() {
    assertThatThrownBy(() -> connect(UUID.randomUUID(), owner))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect("not-a-uuid", owner)).isInstanceOf(ExecutionException.class);
  }

  @Test
  void handshakeIsRejectedWithoutAValidTokenOrMembership() {
    AuthResponse outsider = authService.signup("Eve", uniqueEmail(), "password1");

    assertThatThrownBy(() -> open("/ws/" + docId)).isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> open("/ws/" + docId + "?token=garbage"))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect(docId, outsider)).isInstanceOf(ExecutionException.class);
  }

  private Client connect(Object docId, AuthResponse user) throws Exception {
    return open("/ws/" + docId + "?token=" + user.token());
  }

  private Client open(String path) throws Exception {
    Client client = new Client();
    client.session = new StandardWebSocketClient()
        .execute(client, "ws://localhost:" + port + path)
        .get(5, TimeUnit.SECONDS);
    return client;
  }

  private static String uniqueEmail() {
    return UUID.randomUUID() + "@example.com";
  }

  private static final class Client extends TextWebSocketHandler {

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();
    private WebSocketSession session;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
      messages.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
        CloseStatus status) {
      closed.add(status);
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
