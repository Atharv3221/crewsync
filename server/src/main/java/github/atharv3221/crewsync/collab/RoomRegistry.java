package github.atharv3221.crewsync.collab;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class RoomRegistry {

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
}
