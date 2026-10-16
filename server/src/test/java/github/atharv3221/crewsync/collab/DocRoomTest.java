package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static github.atharv3221.crewsync.collab.TestSessions.insert;
import static github.atharv3221.crewsync.collab.TestSessions.openSession;
import static github.atharv3221.crewsync.collab.TestSessions.received;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

class DocRoomTest {

  private final UUID docId = UUID.randomUUID();
  private final InMemoryOpLog opLog = new InMemoryOpLog();
  private final MessageCodec codec = new MessageCodec(JSON);
  private final WebSocketSession alice = openSession("alice");
  private final WebSocketSession bob = openSession("bob");

  @Test
  void assignsIncreasingSeqsAndBroadcastsToEveryoneIncludingSender() {
    DocRoom room = joinedRoom();

    room.submit("alice", insert(1, "a"));
    room.submit("bob", insert(1, "b"));

    assertThat(received(alice)).hasSize(3);
    assertThat(received(bob)).hasSize(3);
    JsonNode first = received(alice).get(1);
    assertThat(first.get("type").stringValue()).isEqualTo("op");
    assertThat(first.get("seq").longValue()).isEqualTo(1);
    assertThat(first.get("op").get("site").stringValue()).isEqualTo("a");
    assertThat(received(bob).get(2).get("seq").longValue()).isEqualTo(2);
    assertThat(opLog.stored()).extracting(SequencedOp::seq).containsExactly(1L, 2L);
  }

  @Test
  void continuesFromTheHighestStoredSeq() {
    opLog.append(docId, 1, insert(1, "old"));
    opLog.append(docId, 2, insert(2, "old"));
    DocRoom room = joinedRoom();

    room.submit("alice", insert(1, "a"));

    assertThat(received(alice).getLast().get("seq").longValue()).isEqualTo(3);
  }

  @Test
  void ignoresDuplicateOpKeys() {
    DocRoom room = joinedRoom();

    room.submit("alice", insert(1, "a"));
    room.submit("alice", insert(1, "a"));
    room.submit("bob", insert(2, "a"));

    assertThat(opLog.stored()).extracting(SequencedOp::seq).containsExactly(1L, 2L);
    assertThat(received(bob)).extracting(message -> message.path("seq").longValue())
        .containsExactly(0L, 1L, 2L);
  }

  @Test
  void initContainsOnlyOpsAfterLastSeq() {
    opLog.append(docId, 1, insert(1, "a"));
    opLog.append(docId, 2, insert(2, "a"));
    opLog.append(docId, 3, insert(3, "a"));
    DocRoom room = new DocRoom(docId, opLog, codec);
    room.attach(alice);

    room.hello("alice", 1);

    JsonNode init = received(alice).getFirst();
    assertThat(init.get("type").stringValue()).isEqualTo("init");
    assertThat(init.get("seq").longValue()).isEqualTo(3);
    assertThat(init.get("ops")).extracting(op -> op.get("seq").longValue()).containsExactly(2L, 3L);
    assertThat(init.get("ops").get(0).get("op").get("counter").longValue()).isEqualTo(2);
  }

  @Test
  void sessionsReceiveNothingBeforeHello() {
    DocRoom room = new DocRoom(docId, opLog, codec);
    room.attach(alice);
    room.attach(bob);
    room.hello("alice", 0);

    room.submit("alice", insert(1, "a"));

    assertThat(received(bob)).isEmpty();
  }

  @Test
  void rejectsOpsAndPresenceBeforeHello() {
    DocRoom room = new DocRoom(docId, opLog, codec);
    room.attach(alice);

    assertThatThrownBy(() -> room.submit("alice", insert(1, "a")))
        .isInstanceOf(ProtocolException.class);
    assertThatThrownBy(() -> room.relayPresence("alice", "{\"type\":\"presence\"}"))
        .isInstanceOf(ProtocolException.class);
    assertThat(opLog.stored()).isEmpty();
  }

  @Test
  void relaysPresenceAsIsToOthersWithoutPersisting() {
    DocRoom room = joinedRoom();
    String presence = "{\"type\":\"presence\",\"site\":\"a\",\"cursor\":null,\"extra\":[1,2]}";

    room.relayPresence("alice", presence);

    assertThat(received(bob).getLast()).isEqualTo(JSON.readTree(presence));
    assertThat(received(alice)).hasSize(1);
    assertThat(opLog.stored()).isEmpty();
  }

  @Test
  void skipsClosedSessions() {
    DocRoom room = joinedRoom();
    when(bob.isOpen()).thenReturn(false);

    room.submit("alice", insert(1, "a"));

    assertThat(received(alice)).hasSize(2);
    assertThat(received(bob)).hasSize(1);
  }

  @Test
  void reportsWhenLastSessionLeaves() {
    DocRoom room = joinedRoom();

    assertThat(room.detach("alice")).isFalse();
    assertThat(room.detach("bob")).isTrue();
  }

  private DocRoom joinedRoom() {
    DocRoom room = new DocRoom(docId, opLog, codec);
    room.attach(alice);
    room.attach(bob);
    room.hello("alice", 0);
    room.hello("bob", 0);
    return room;
  }
}
