package github.atharv3221.crewsync.collab;

import java.util.UUID;
import org.springframework.web.socket.WebSocketSession;

/** The authenticated user behind a WebSocket session, resolved once at handshake. */
public record SessionUser(UUID id, String name) {

  static SessionUser of(WebSocketSession session) {
    return (SessionUser) session.getAttributes().get(DocHandshakeInterceptor.USER_ATTRIBUTE);
  }
}
