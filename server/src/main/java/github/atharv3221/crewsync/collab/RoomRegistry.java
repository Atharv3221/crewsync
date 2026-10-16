package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.collab.DocRoom.Batching;
import github.atharv3221.crewsync.document.DocumentRenamedEvent;
import github.atharv3221.crewsync.document.MemberRemovedEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Opens a room per document on demand and drops it once it has no sessions and nothing left to
 * store. A room's lock is never taken inside a map operation, so a session closing itself while a
 * room sends to it can't deadlock with a session entering.
 */
@Slf4j
@Component
public class RoomRegistry {

  /** Close code for a session whose user lost access; 4403 mirrors HTTP 403. */
  static final CloseStatus ACCESS_REVOKED = new CloseStatus(4403, "Access revoked");

  /** Up to 100 ops, or what arrives within 5 ms of the oldest, per transaction. */
  static final Batching BATCHING = new Batching(100, Duration.ofMillis(5));

  private final OpLog opLog;
  private final MessageCodec codec;
  private final Batching batching;
  private final Executor writers;
  private final ConcurrentMap<UUID, DocRoom> rooms = new ConcurrentHashMap<>();

  /** Each room's writer runs on its own virtual thread while it has ops to store. */
  @Autowired
  public RoomRegistry(OpLog opLog, MessageCodec codec) {
    this(opLog, codec, BATCHING, Executors.newVirtualThreadPerTaskExecutor());
  }

  RoomRegistry(OpLog opLog, MessageCodec codec, Batching batching, Executor writers) {
    this.opLog = opLog;
    this.codec = codec;
    this.batching = batching;
    this.writers = writers;
  }

  DocRoom enter(UUID docId, WebSocketSession session) {
    DocRoom room = rooms.computeIfAbsent(docId, this::open);
    while (!room.attach(session)) {
      rooms.remove(docId, room); // closed after we looked it up: replace it
      room = rooms.computeIfAbsent(docId, this::open);
    }
    return room;
  }

  void leave(UUID docId, String sessionId) {
    Optional.ofNullable(rooms.get(docId))
        .filter(room -> room.detach(sessionId))
        .ifPresent(room -> rooms.remove(docId, room));
  }

  /** Runs after the removal commits, so a reconnect attempt is already refused. */
  @TransactionalEventListener
  public void onMemberRemoved(MemberRemovedEvent event) {
    evict(event.docId(), event.userId());
  }

  @TransactionalEventListener
  public void onDocumentRenamed(DocumentRenamedEvent event) {
    Optional.ofNullable(rooms.get(event.docId()))
        .ifPresent(room -> room.announceTitle(event.title(), event.titleVersion()));
  }

  void evict(UUID docId, UUID userId) {
    Optional.ofNullable(rooms.get(docId))
        .map(room -> room.evict(userId))
        .orElse(List.of())
        .forEach(RoomRegistry::closeRevoked);
  }

  private DocRoom open(UUID docId) {
    return new DocRoom(docId, opLog, codec, batching, writers, this::dropIfIdle);
  }

  /** Called when a room's writer stops: the last session may have left while it was storing. */
  private void dropIfIdle(DocRoom room) {
    if (room.closeIfIdle()) {
      rooms.remove(room.docId(), room);
    }
  }

  private static void closeRevoked(WebSocketSession session) {
    try {
      session.close(ACCESS_REVOKED);
    } catch (IOException exception) {
      log.debug("Could not close revoked session {}", session.getId(), exception);
    }
  }
}
