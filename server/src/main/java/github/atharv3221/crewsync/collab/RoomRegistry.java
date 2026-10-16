package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.document.DocumentRenamedEvent;
import github.atharv3221.crewsync.document.MemberRemovedEvent;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomRegistry {

  /** Close code for a session whose user lost access; 4403 mirrors HTTP 403. */
  static final CloseStatus ACCESS_REVOKED = new CloseStatus(4403, "Access revoked");

  private final OpLog opLog;
  private final MessageCodec codec;
  private final ConcurrentMap<UUID, DocRoom> rooms = new ConcurrentHashMap<>();

  DocRoom enter(UUID docId, WebSocketSession session) {
    return rooms.compute(docId, (id, room) -> {
      DocRoom target = room == null ? new DocRoom(id, opLog, codec) : room;
      target.attach(session);
      return target;
    });
  }

  void leave(UUID docId, String sessionId) {
    rooms.computeIfPresent(docId, (id, room) -> room.detach(sessionId) ? null : room);
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

  private static void closeRevoked(WebSocketSession session) {
    try {
      session.close(ACCESS_REVOKED);
    } catch (IOException exception) {
      log.debug("Could not close revoked session {}", session.getId(), exception);
    }
  }
}
