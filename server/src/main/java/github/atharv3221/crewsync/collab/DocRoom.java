package github.atharv3221.crewsync.collab;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/**
 * All sessions editing one document. Every operation holds the room's lock, so seqs are assigned
 * and persisted in order and a session's init can never interleave with a broadcast.
 * Sessions only receive broadcasts after their hello has been answered.
 */
@Slf4j
final class DocRoom {

  private final UUID docId;
  private final OpLog opLog;
  private final MessageCodec codec;
  private final Map<String, WebSocketSession> sessions = new HashMap<>();
  private final Set<String> joined = new HashSet<>();
  private long seq;

  DocRoom(UUID docId, OpLog opLog, MessageCodec codec) {
    this.docId = docId;
    this.opLog = opLog;
    this.codec = codec;
    this.seq = opLog.maxSeq(docId);
  }

  synchronized void attach(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  /**
   * @return true when no sessions remain and the room can be discarded
   */
  synchronized boolean detach(String sessionId) {
    sessions.remove(sessionId);
    joined.remove(sessionId);
    return sessions.isEmpty();
  }

  synchronized void hello(String sessionId, long lastSeq) {
    send(sessions.get(sessionId), codec.init(seq, opLog.opsAfter(docId, lastSeq)));
    joined.add(sessionId);
  }

  synchronized void submit(String sessionId, ClientOp op) {
    requireJoined(sessionId);
    if (opLog.append(docId, seq + 1, op)) {
      seq++;
      broadcast(codec.op(seq, op), null);
    }
  }

  synchronized void relayPresence(String sessionId, String payload) {
    requireJoined(sessionId);
    broadcast(new TextMessage(payload), sessionId);
  }

  private void requireJoined(String sessionId) {
    if (!joined.contains(sessionId)) {
      throw new ProtocolException("Send hello first");
    }
  }

  private void broadcast(TextMessage message, String excludedSessionId) {
    joined.stream()
        .filter(id -> !id.equals(excludedSessionId))
        .map(sessions::get)
        .forEach(session -> send(session, message));
  }

  private void send(WebSocketSession session, TextMessage message) {
    try {
      if (session.isOpen()) {
        session.sendMessage(message);
      }
    } catch (IOException | SessionLimitExceededException exception) {
      log.debug("Dropping message to session {} in doc {}", session.getId(), docId, exception);
    }
  }
}
