package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static github.atharv3221.crewsync.collab.TestSessions.insert;
import static org.assertj.core.api.Assertions.assertThat;

import github.atharv3221.crewsync.collab.DocRoom.Batching;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.web.socket.CloseStatus;

/**
 * Broadcasts run while other threads join, leave, get evicted and break their connections. Before
 * the fix, a session closing itself during a broadcast (as Tomcat does on a failed send) removed
 * itself from the set being iterated, and the broadcast threw ConcurrentModificationException.
 */
class DocRoomConcurrencyTest {

  private static final int STABLE_SESSIONS = 4;
  private static final int OPS_PER_STABLE_SESSION = 300;
  private static final int CHURN_SESSIONS = 200;
  private static final int EVICTIONS = 50;

  private final UUID docId = UUID.randomUUID();
  private final InMemoryOpLog opLog = new InMemoryOpLog();
  private final RoomRegistry registry = new RoomRegistry(opLog, new MessageCodec(JSON),
      new Batching(20, Duration.ofMillis(1)), Executors.newVirtualThreadPerTaskExecutor());
  private final AtomicInteger acceptedOps = new AtomicInteger();
  private final Queue<FakeSession> brokenSessions = new ConcurrentLinkedQueue<>();
  private final Queue<FakeSession> healthySessions = new ConcurrentLinkedQueue<>();
  private final Queue<FakeSession> evictedSessions = new ConcurrentLinkedQueue<>();
  private final Map<String, DocRoom> roomOf = new ConcurrentHashMap<>();

  @RepeatedTest(5)
  void broadcastsStayOrderedAndGapFreeWhileSessionsComeAndGo() throws Exception {
    List<FakeSession> stable = new ArrayList<>();
    for (int i = 0; i < STABLE_SESSIONS; i++) {
      stable.add(join("stable-" + i, UUID.randomUUID()));
    }

    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < STABLE_SESSIONS; i++) {
      String id = "stable-" + i;
      tasks.add(() -> {
        start.await();
        for (int counter = 1; counter <= OPS_PER_STABLE_SESSION; counter++) {
          submit(id, insert(counter, id));
        }
        return null;
      });
    }
    tasks.add(() -> churn(start));
    tasks.add(() -> evictions(start));

    ExecutorService threads = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<Void>> futures = new ArrayList<>();
      tasks.forEach(task -> futures.add(threads.submit(task)));
      start.countDown();
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS); // rethrows anything a thread threw
      }
    } finally {
      threads.shutdownNow();
    }

    // one more broadcast reaches every session that broke after the last one and closes it
    submit("stable-0", insert(OPS_PER_STABLE_SESSION + 1, "stable-0"));
    int total = acceptedOps.get();
    awaitStored(total);
    List<Long> expected = LongStream.rangeClosed(1, total).boxed().toList();
    assertThat(opLog.stored()).extracting(SequencedOp::seq).isEqualTo(expected);
    awaitReceived(stable, total);
    for (FakeSession session : stable) {
      assertThat(opSeqs(session)).as(session.getId()).isEqualTo(expected);
      assertThat(session.isOpen()).isTrue();
    }
    for (FakeSession broken : brokenSessions) {
      assertThat(broken.closeStatus()).as(broken.getId())
          .isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);
    }
    for (FakeSession healthy : healthySessions) {
      assertThat(healthy.closeStatus()).as(healthy.getId()).isEqualTo(CloseStatus.NORMAL);
    }
    for (FakeSession evicted : evictedSessions) {
      assertThat(evicted.closeStatus()).as(evicted.getId()).isEqualTo(RoomRegistry.ACCESS_REVOKED);
    }
    assertThat(roomOf.values()).as("stable sessions keep one room alive").containsOnly(
        roomOf.get("stable-0"));
  }

  /** Sessions join, sometimes type, sometimes break, and leave. */
  private Void churn(CountDownLatch start) throws InterruptedException {
    start.await();
    Random random = new Random(42);
    for (int i = 0; i < CHURN_SESSIONS; i++) {
      String id = "churn-" + i;
      FakeSession session = join(id, UUID.randomUUID());
      if (random.nextInt(4) == 0) {
        session.breakConnection(random.nextBoolean());
        brokenSessions.add(session);
      } else {
        healthySessions.add(session);
      }
      if (random.nextBoolean()) {
        submit(id, insert(1, id));
      }
      Thread.sleep(0, 200_000);
      if (!brokenSessions.contains(session)) {
        session.close();
      }
    }
    return null;
  }

  /** Two tabs of one user join and the owner removes that user. */
  private Void evictions(CountDownLatch start) throws InterruptedException {
    start.await();
    for (int i = 0; i < EVICTIONS; i++) {
      UUID userId = UUID.randomUUID();
      FakeSession first = join("evicted-" + i + "-a", userId);
      FakeSession second = join("evicted-" + i + "-b", userId);
      evictedSessions.add(first);
      evictedSessions.add(second);
      Thread.sleep(0, 500_000);
      registry.evict(docId, userId);
      assertThat(first.isOpen()).isFalse();
      assertThat(second.isOpen()).isFalse();
    }
    return null;
  }

  private FakeSession join(String id, UUID userId) {
    FakeSession session = new FakeSession(id, userId, closed -> registry.leave(docId, id));
    DocRoom room = registry.enter(docId, session);
    roomOf.put(id, room);
    room.hello(id, 0);
    return session;
  }

  /** Submits unless the session was already closed by the room; counts what was accepted. */
  private void submit(String sessionId, ClientOp op) {
    try {
      roomOf.get(sessionId).submit(sessionId, op);
      acceptedOps.incrementAndGet();
    } catch (ProtocolException notJoined) {
      // closed by the room after a failed send: the real handler would get no more messages
    }
  }

  private void awaitStored(int total) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (opLog.stored().size() < total && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
  }

  private static void awaitReceived(List<FakeSession> sessions, int total)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (sessions.stream().anyMatch(s -> opSeqs(s).size() < total)
        && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
  }

  private static List<Long> opSeqs(FakeSession session) {
    return session.received().stream()
        .map(JSON::readTree)
        .filter(message -> message.get("type").stringValue().equals("op"))
        .map(message -> message.get("seq").longValue())
        .toList();
  }
}
