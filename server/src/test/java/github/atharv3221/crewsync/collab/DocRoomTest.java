package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static github.atharv3221.crewsync.collab.TestSessions.insert;
import static github.atharv3221.crewsync.collab.TestSessions.openSession;
import static github.atharv3221.crewsync.collab.TestSessions.received;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
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
  void announcesTitlesToEveryoneAndDropsOnesOlderThanTheLastSent() {
    DocRoom room = joinedRoom();

    room.announceTitle("Second", 2);
    room.announceTitle("First", 1);
    room.announceTitle("Second again", 2);
    room.announceTitle("Third", 3);

    for (WebSocketSession session : List.of(alice, bob)) {
      assertThat(received(session).subList(1, received(session).size()))
          .extracting(message -> message.get("title").stringValue())
          .containsExactly("Second", "Third");
    }
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
  void storesDuplicateOpKeysOnce() {
    DocRoom room = joinedRoom();

    room.submit("alice", insert(1, "a"));
    room.submit("alice", insert(1, "a"));
    room.submit("bob", insert(2, "a"));

    assertThat(opLog.stored()).extracting(SequencedOp::seq).containsExactly(1L, 2L);
    assertThat(received(bob)).extracting(message -> message.path("seq").longValue())
        .containsExactly(0L, 1L, 2L);
  }

  @Test
  void answersADuplicateToItsSenderOnlyWithTheOriginalSeq() {
    DocRoom room = joinedRoom();
    room.submit("alice", insert(1, "a"));
    room.submit("bob", insert(1, "b"));

    room.submit("bob", insert(1, "a"));

    JsonNode ack = received(bob).getLast();
    assertThat(ack.get("type").stringValue()).isEqualTo("op");
    assertThat(ack.get("seq").longValue()).isEqualTo(1);
    assertThat(ack.get("op")).isEqualTo(received(alice).get(1).get("op"));
    assertThat(received(alice)).hasSize(3);
    assertThat(opLog.stored()).hasSize(2);
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
    assertThatThrownBy(() -> room.relayPresence("alice", new TextMessage("{\"type\":\"presence\"}")))
        .isInstanceOf(ProtocolException.class);
    assertThat(opLog.stored()).isEmpty();
  }

  @Test
  void relaysPresenceToOthersWithoutPersisting() {
    DocRoom room = joinedRoom();
    String presence = "{\"type\":\"presence\",\"site\":\"a\",\"cursor\":null,\"extra\":[1,2]}";

    room.relayPresence("alice", new TextMessage(presence));

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

  @Test
  void evictRemovesAllTabsOfTheUserAndTellsTheOthers() {
    UUID removedId = UUID.randomUUID();
    WebSocketSession keeper = openSession("keeper", UUID.randomUUID());
    WebSocketSession tab1 = openSession("tab1", removedId);
    WebSocketSession tab2 = openSession("tab2", removedId);
    DocRoom room = new DocRoom(docId, opLog, codec);
    for (WebSocketSession session : List.of(keeper, tab1, tab2)) {
      room.attach(session);
      room.hello(session.getId(), 0);
    }

    List<WebSocketSession> evicted = room.evict(removedId);

    assertThat(evicted).containsExactlyInAnyOrder(tab1, tab2);
    JsonNode leave = received(keeper).getLast();
    assertThat(leave.get("type").stringValue()).isEqualTo("leave");
    assertThat(leave.get("userId").stringValue()).isEqualTo(removedId.toString());
    assertThat(received(tab1)).hasSize(1);

    room.submit("keeper", insert(1, "k"));
    assertThat(received(tab1)).hasSize(1);
    assertThatThrownBy(() -> room.submit("tab1", insert(2, "t")))
        .isInstanceOf(ProtocolException.class);
    assertThat(room.detach("keeper")).isTrue();
  }

  @Test
  void evictingAUserWithoutSessionsChangesNothing() {
    WebSocketSession keeper = openSession("keeper", UUID.randomUUID());
    DocRoom room = new DocRoom(docId, opLog, codec);
    room.attach(keeper);
    room.hello("keeper", 0);

    assertThat(room.evict(UUID.randomUUID())).isEmpty();
    assertThat(received(keeper)).hasSize(1);
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
