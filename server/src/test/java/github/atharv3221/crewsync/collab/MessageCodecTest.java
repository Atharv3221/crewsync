package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.atharv3221.crewsync.collab.ClientMessage.Hello;
import github.atharv3221.crewsync.collab.ClientMessage.Presence;
import github.atharv3221.crewsync.collab.ClientMessage.Submit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class MessageCodecTest {

  private final MessageCodec codec = new MessageCodec(JSON);

  @Test
  void decodesHello() {
    assertThat(codec.decode("{\"type\":\"hello\",\"site\":\"s1\",\"lastSeq\":7}"))
        .isEqualTo(new Hello(7));
  }

  @Test
  void buildsInsertKeyAndKeepsBodyOpaque() {
    String op = "{\"kind\":\"insert\",\"counter\":3,\"site\":\"s1\",\"value\":\"x\","
        + "\"origin\":{\"counter\":2,\"site\":\"s0\"},\"future\":true}";

    Submit submit = (Submit) codec.decode("{\"type\":\"op\",\"op\":" + op + "}");

    assertThat(submit.op().key()).isEqualTo("insert:3@s1");
    assertThat(JSON.readTree(submit.op().json())).isEqualTo(JSON.readTree(op));
  }

  @Test
  void acceptsInsertAtDocumentStart() {
    Submit submit = (Submit) codec.decode("{\"type\":\"op\",\"op\":{\"kind\":\"insert\","
        + "\"counter\":1,\"site\":\"s1\",\"value\":\"a\",\"origin\":null}}");

    assertThat(submit.op().key()).isEqualTo("insert:1@s1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\\n", " ", "\\t", "\\r"})
  void acceptsWhitespaceInsertValues(String escapedValue) {
    Submit submit = (Submit) codec.decode("{\"type\":\"op\",\"op\":{\"kind\":\"insert\","
        + "\"counter\":1,\"site\":\"s1\",\"value\":\"" + escapedValue + "\",\"origin\":null}}");

    assertThat(submit.op().body().get("value").stringValue()).isNotEmpty().isBlank();
  }

  @Test
  void rejectsEmptyInsertValue() {
    assertThatThrownBy(() -> codec.decode("{\"type\":\"op\",\"op\":{\"kind\":\"insert\","
        + "\"counter\":1,\"site\":\"s1\",\"value\":\"\",\"origin\":null}}"))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void buildsDeleteKey() {
    Submit submit = (Submit) codec.decode("{\"type\":\"op\",\"op\":{\"kind\":\"delete\","
        + "\"counter\":4,\"site\":\"s1\",\"target\":{\"counter\":3,\"site\":\"s1\"}}}");

    assertThat(submit.op().key()).isEqualTo("delete:4@s1");
  }

  @Test
  void keepsPresenceMessage() {
    String payload = "{\"type\":\"presence\",\"site\":\"s1\",\"cursor\":{\"counter\":1}}";

    assertThat(codec.decode(payload)).isEqualTo(new Presence((ObjectNode) JSON.readTree(payload)));
  }

  @Test
  void presenceIdentityIsOverwrittenWithTheSessionUser() {
    UUID realId = UUID.randomUUID();
    Presence presence = (Presence) codec.decode("{\"type\":\"presence\",\"site\":\"s1\","
        + "\"userId\":\"someone-else\",\"name\":\"Mallory\",\"cursor\":null}");

    JsonNode relayed = JSON.readTree(
        codec.presence(presence.message(), new SessionUser(realId, "Ada")).getPayload());

    assertThat(relayed.get("userId").stringValue()).isEqualTo(realId.toString());
    assertThat(relayed.get("name").stringValue()).isEqualTo("Ada");
    assertThat(relayed.get("site").stringValue()).isEqualTo("s1");
    assertThat(relayed.get("cursor").isNull()).isTrue();
  }

  @Test
  void presenceIdentityIsAddedWhenMissing() {
    Presence presence = (Presence) codec.decode("{\"type\":\"presence\",\"site\":\"s1\"}");

    JsonNode relayed = JSON.readTree(codec.presence(
        presence.message(), new SessionUser(UUID.randomUUID(), "Ada")).getPayload());

    assertThat(relayed.get("name").stringValue()).isEqualTo("Ada");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "not json",
      "[]",
      "{}",
      "{\"type\":\"bogus\"}",
      "{\"type\":\"hello\"}",
      "{\"type\":\"hello\",\"lastSeq\":-1}",
      "{\"type\":\"hello\",\"lastSeq\":1.5}",
      "{\"type\":\"op\"}",
      "{\"type\":\"op\",\"op\":\"insert\"}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"move\",\"counter\":1,\"site\":\"s\"}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"site\":\"s\",\"value\":\"x\",\"origin\":null}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":\"1\",\"site\":\"s\","
          + "\"value\":\"x\",\"origin\":null}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":1,\"site\":\"\","
          + "\"value\":\"x\",\"origin\":null}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":1,\"site\":\"s\",\"value\":\"x\"}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":1,\"site\":\"s\","
          + "\"value\":\"x\",\"origin\":{\"counter\":1}}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"insert\",\"counter\":1,\"site\":\"s\",\"origin\":null}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"delete\",\"counter\":1,\"site\":\"s\"}}",
      "{\"type\":\"op\",\"op\":{\"kind\":\"delete\",\"counter\":1,\"site\":\"s\",\"target\":null}}"
  })
  void rejectsMalformedMessages(String payload) {
    assertThatThrownBy(() -> codec.decode(payload)).isInstanceOf(ProtocolException.class);
  }

  @Test
  void rejectsOverlongSite() {
    String site = "s".repeat(ClientOp.MAX_SITE_LENGTH + 1);

    assertThatThrownBy(() -> codec.decode("{\"type\":\"op\",\"op\":{\"kind\":\"insert\","
        + "\"counter\":1,\"site\":\"" + site + "\",\"value\":\"x\",\"origin\":null}}"))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void encodesTitleChange() {
    JsonNode message = JSON.readTree(codec.title("Q4 \"plan\"", 3).getPayload());

    assertThat(message.get("type").stringValue()).isEqualTo("title");
    assertThat(message.get("title").stringValue()).isEqualTo("Q4 \"plan\"");
    assertThat(message.get("version").longValue()).isEqualTo(3);
  }

  @Test
  void encodesOpBroadcast() {
    JsonNode message = JSON.readTree(codec.op(
        new SequencedOp(5, TestSessions.insert(1, "s1").json())).getPayload());

    assertThat(message.get("type").stringValue()).isEqualTo("op");
    assertThat(message.get("seq").longValue()).isEqualTo(5);
    assertThat(message.get("op").get("kind").stringValue()).isEqualTo("insert");
  }

  @Test
  void encodesInitWithSequencedOps() {
    List<SequencedOp> ops = List.of(new SequencedOp(4, "{\"kind\":\"delete\"}"));

    JsonNode message = JSON.readTree(codec.init(4, ops).getPayload());

    assertThat(message.get("type").stringValue()).isEqualTo("init");
    assertThat(message.get("seq").longValue()).isEqualTo(4);
    assertThat(message.get("ops").get(0).get("seq").longValue()).isEqualTo(4);
    assertThat(message.get("ops").get(0).get("op").get("kind").stringValue()).isEqualTo("delete");
  }
}
