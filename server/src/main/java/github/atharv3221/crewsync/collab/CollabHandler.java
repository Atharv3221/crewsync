package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.collab.ClientMessage.Hello;
import github.atharv3221.crewsync.collab.ClientMessage.Presence;
import github.atharv3221.crewsync.collab.ClientMessage.Submit;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class CollabHandler extends TextWebSocketHandler {

  private static final String ROOM_ATTRIBUTE = "room";
  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

  private final RoomRegistry rooms;
  private final MessageCodec codec;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(
        session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
    session.getAttributes().put(ROOM_ATTRIBUTE, rooms.enter(docIdOf(session), concurrent));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {
    try {
      dispatch(session, codec.decode(message.getPayload()));
    } catch (ProtocolException exception) {
      session.close(CloseStatus.BAD_DATA.withReason(exception.getMessage()));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    rooms.leave(docIdOf(session), session.getId());
  }

  private void dispatch(WebSocketSession session, ClientMessage message) {
    DocRoom room = roomOf(session);
    switch (message) {
      case Hello hello -> room.hello(session.getId(), hello.lastSeq());
      case Submit submit -> room.submit(session.getId(), submit.op());
      case Presence presence -> room.relayPresence(
          session.getId(), codec.presence(presence.message(), SessionUser.of(session)));
    }
  }

  private static UUID docIdOf(WebSocketSession session) {
    return (UUID) session.getAttributes().get(DocHandshakeInterceptor.DOC_ID_ATTRIBUTE);
  }

  private static DocRoom roomOf(WebSocketSession session) {
    return (DocRoom) session.getAttributes().get(ROOM_ATTRIBUTE);
  }
}
