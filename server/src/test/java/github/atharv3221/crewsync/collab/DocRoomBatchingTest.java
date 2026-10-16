package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static github.atharv3221.crewsync.collab.TestSessions.NO_WAIT;
import static github.atharv3221.crewsync.collab.TestSessions.insert;
import static org.assertj.core.api.Assertions.assertThat;

import github.atharv3221.crewsync.collab.DocRoom.Batching;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.JsonNode;

/** Group commit: queue under the lock, one writer stores a batch, broadcast after the commit. */
class DocRoomBatchingTest {

  private final UUID docId = UUID.randomUUID();
  private final InMemoryOpLog opLog = new InMemoryOpLog();
  private final ManualExecutor writer = new ManualExecutor();

  /** Holds the writer task until the test runs it, so ops can pile up in the queue. */
  private static final class ManualExecutor implements Executor {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }

    void runAll() {
      while (!tasks.isEmpty()) {
        tasks.poll().run();
      }
    }
  }

  @Test
  void storesOpsQueuedTogetherInOneTransactionAndBroadcastsThemInSeqOrder() {
    DocRoom room = room(NO_WAIT);
    FakeSession alice = join(room, "alice");
    FakeSession bob = join(room, "bob");

    for (int counter = 1; counter <= 5; counter++) {
      room.submit(counter % 2 == 0 ? "bob" : "alice", insert(counter, "s"));
    }
    writer.runAll();

    assertThat(opLog.batchSizes()).containsExactly(5);
    for (FakeSession session : List.of(alice, bob)) {
      assertThat(opSeqs(session)).containsExactly(1L, 2L, 3L, 4L, 5L);
    }
  }

  @Test
  void capsABatchAtMaxOps() {
    DocRoom room = room(new Batching(3, Duration.ZERO));
    FakeSession alice = join(room, "alice");

    for (int counter = 1; counter <= 7; counter++) {
      room.submit("alice", insert(counter, "a"));
    }
    writer.runAll();

    assertThat(opLog.batchSizes()).containsExactly(3, 3, 1);
    assertThat(opSeqs(alice)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
  }

  @Test
  void broadcastsNothingBeforeTheBatchIsStored() {
    DocRoom room = room(NO_WAIT);
    FakeSession alice = join(room, "alice");
    FakeSession bob = join(room, "bob");

    room.submit("alice", insert(1, "a"));

    assertThat(opLog.stored()).isEmpty();
    assertThat(opSeqs(alice)).isEmpty();
    assertThat(opSeqs(bob)).isEmpty();
    writer.runAll();
    assertThat(opSeqs(bob)).containsExactly(1L);
  }

  @Test
  void aSessionJoiningWhileOpsAreQueuedGetsThemByBroadcastNotInit() {
    DocRoom room = room(NO_WAIT);
    join(room, "alice");
    room.submit("alice", insert(1, "a"));

    FakeSession carol = join(room, "carol");
    writer.runAll();

    JsonNode init = JSON.readTree(carol.received().getFirst());
    assertThat(init.get("seq").longValue()).isZero();
    assertThat(init.get("ops")).isEmpty();
    assertThat(opSeqs(carol)).containsExactly(1L);
  }

  @Test
  void aDuplicateInTheSameBatchIsStoredOnceAndReAckedToItsSender() {
    DocRoom room = room(NO_WAIT);
    FakeSession alice = join(room, "alice");
    FakeSession bob = join(room, "bob");

    room.submit("alice", insert(1, "a"));
    room.submit("alice", insert(1, "a"));
    room.submit("bob", insert(1, "b"));
    writer.runAll();

    assertThat(opLog.stored()).extracting(SequencedOp::seq).containsExactly(1L, 2L);
    assertThat(opSeqs(alice)).containsExactly(1L, 2L, 1L);
    assertThat(opSeqs(bob)).containsExactly(1L, 2L);
  }

  @Test
  void retriesAFailedBatchOnce() {
    DocRoom room = room(NO_WAIT);
    FakeSession alice = join(room, "alice");
    opLog.failNextAppends(1);

    room.submit("alice", insert(1, "a"));
    writer.runAll();

    assertThat(opLog.stored()).hasSize(1);
    assertThat(opSeqs(alice)).containsExactly(1L);
    assertThat(alice.isOpen()).isTrue();
  }

  @Test
  void closesOnlyTheSendersOfABatchThatFailsTwiceAndUsesNoSeq() {
    DocRoom room = room(NO_WAIT);
    FakeSession alice = join(room, "alice");
    FakeSession bob = join(room, "bob");
    opLog.failNextAppends(2);

    room.submit("alice", insert(1, "a"));
    writer.runAll();

    assertThat(alice.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
    assertThat(bob.isOpen()).isTrue();
    assertThat(opSeqs(bob)).isEmpty();

    room.submit("bob", insert(1, "b"));
    writer.runAll();
    assertThat(opSeqs(bob)).containsExactly(1L);
  }

  @Test
  void groupsOpsArrivingWithinMaxWaitOfTheOldest() throws Exception {
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    DocRoom room = new DocRoom(docId, opLog, new MessageCodec(JSON),
        new Batching(100, Duration.ofMillis(300)), executor, ignored -> {
        });
    FakeSession alice = join(room, "alice");

    for (int counter = 1; counter <= 5; counter++) {
      room.submit("alice", insert(counter, "a"));
    }
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    assertThat(opLog.batchSizes()).containsExactly(5);
    assertThat(opSeqs(alice)).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void theRoomIsDroppedOnlyAfterItsWriterStoredEverything() {
    RoomRegistry registry = new RoomRegistry(opLog, new MessageCodec(JSON), NO_WAIT, writer);
    FakeSession alice = new FakeSession("alice", UUID.randomUUID(), ignored -> {
    });
    DocRoom room = registry.enter(docId, alice);
    room.hello("alice", 0);
    room.submit("alice", insert(1, "a"));

    registry.leave(docId, "alice");
    FakeSession bob = new FakeSession("bob", UUID.randomUUID(), ignored -> {
    });
    assertThat(registry.enter(docId, bob)).isSameAs(room);
    registry.leave(docId, "bob");

    writer.runAll();
    DocRoom reopened = registry.enter(docId, bob);
    assertThat(reopened).isNotSameAs(room);
    reopened.hello("bob", 0);
    assertThat(JSON.readTree(bob.received().getLast()).get("seq").longValue()).isEqualTo(1);
  }

  private DocRoom room(Batching batching) {
    return new DocRoom(docId, opLog, new MessageCodec(JSON), batching, writer, ignored -> {
    });
  }

  private static FakeSession join(DocRoom room, String id) {
    FakeSession session = new FakeSession(id, UUID.randomUUID(), ignored -> {
    });
    room.attach(session);
    room.hello(id, 0);
    return session;
  }

  /** Seqs of the op messages a session received, in arrival order. */
  private static List<Long> opSeqs(FakeSession session) {
    return session.received().stream()
        .map(JSON::readTree)
        .filter(message -> message.get("type").stringValue().equals("op"))
        .map(message -> message.get("seq").longValue())
        .toList();
  }
}
