package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.TestSessions.JSON;
import static github.atharv3221.crewsync.collab.TestSessions.openSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class RoomRegistryTest {

  private final InMemoryOpLog opLog = new InMemoryOpLog();
  private final RoomRegistry registry = new RoomRegistry(opLog, new MessageCodec(JSON));
  private final UUID docId = UUID.randomUUID();
  private final WebSocketSession alice = openSession("alice");
  private final WebSocketSession bob = openSession("bob");

  @Test
  void sessionsOfTheSameDocShareOneRoom() {
    DocRoom first = registry.enter(docId, alice);
    DocRoom second = registry.enter(docId, bob);

    assertThat(second).isSameAs(first);
    assertThat(opLog.maxSeqLoads()).isEqualTo(1);
  }

  @Test
  void differentDocsGetDifferentRooms() {
    assertThat(registry.enter(docId, alice)).isNotSameAs(registry.enter(UUID.randomUUID(), bob));
  }

  @Test
  void roomStaysOpenWhileSessionsRemain() {
    DocRoom room = registry.enter(docId, alice);
    registry.enter(docId, bob);

    registry.leave(docId, "alice");

    assertThat(registry.enter(docId, alice)).isSameAs(room);
  }

  @Test
  void evictClosesOnlyTheRemovedUsersSessionsWith4403() throws Exception {
    UUID removedId = UUID.randomUUID();
    WebSocketSession keeper = openSession("keeper", UUID.randomUUID());
    WebSocketSession removed = openSession("removed", removedId);
    registry.enter(docId, keeper);
    registry.enter(docId, removed);

    registry.evict(docId, removedId);

    verify(removed).close(RoomRegistry.ACCESS_REVOKED);
    verify(keeper, never()).close(any());
    assertThat(RoomRegistry.ACCESS_REVOKED.getCode()).isEqualTo(4403);
  }

  @Test
  void evictWithoutAnOpenRoomDoesNothing() {
    registry.evict(UUID.randomUUID(), UUID.randomUUID());

    assertThat(opLog.maxSeqLoads()).isZero();
  }

  @Test
  void emptyRoomIsRemovedAndReopenedFromTheLog() {
    DocRoom room = registry.enter(docId, alice);

    registry.leave(docId, "alice");
    DocRoom reopened = registry.enter(docId, alice);

    assertThat(reopened).isNotSameAs(room);
    assertThat(opLog.maxSeqLoads()).isEqualTo(2);
  }
}
