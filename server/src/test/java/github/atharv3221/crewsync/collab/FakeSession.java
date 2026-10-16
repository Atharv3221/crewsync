package github.atharv3221.crewsync.collab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * A thread-safe session for concurrency tests. Like Tomcat, closing it runs the close callback on
 * the closing thread, and a session marked broken fails its sends and closes itself while sending.
 */
final class FakeSession implements WebSocketSession {

  private final String id;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();
  private final Queue<String> received = new ConcurrentLinkedQueue<>();
  private final Consumer<FakeSession> onClose;
  private volatile boolean open = true;
  private volatile boolean broken;
  private volatile boolean closesItself;
  private volatile CloseStatus closeStatus;

  FakeSession(String id, UUID userId, Consumer<FakeSession> onClose) {
    this.id = id;
    this.onClose = onClose;
    attributes.put(DocHandshakeInterceptor.USER_ATTRIBUTE, new SessionUser(userId, id));
  }

  /**
   * From now on every send fails. If {@code closesItself}, the session first closes itself on the
   * sending thread, as Tomcat does on a broken pipe; otherwise it stays open, like a session over
   * its send buffer limit, and whoever sent must close it.
   */
  void breakConnection(boolean closesItself) {
    this.closesItself = closesItself;
    broken = true;
  }

  List<String> received() {
    return List.copyOf(received);
  }

  CloseStatus closeStatus() {
    return closeStatus;
  }

  @Override
  public void sendMessage(WebSocketMessage<?> message) throws IOException {
    if (broken) {
      if (closesItself) {
        close(CloseStatus.SESSION_NOT_RELIABLE);
      }
      throw new IOException("Broken pipe");
    }
    if (open) {
      received.add(((TextMessage) message).getPayload());
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    close(CloseStatus.NORMAL);
  }

  @Override
  public void close(CloseStatus status) {
    boolean wasOpen;
    synchronized (this) {
      wasOpen = open;
      open = false;
      if (wasOpen) {
        closeStatus = status;
      }
    }
    if (wasOpen) {
      onClose.accept(this);
    }
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public URI getUri() {
    return null;
  }

  @Override
  public HttpHeaders getHandshakeHeaders() {
    return new HttpHeaders();
  }

  @Override
  public Principal getPrincipal() {
    return null;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return null;
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return null;
  }

  @Override
  public String getAcceptedProtocol() {
    return null;
  }

  @Override
  public void setTextMessageSizeLimit(int messageSizeLimit) {
  }

  @Override
  public int getTextMessageSizeLimit() {
    return 0;
  }

  @Override
  public void setBinaryMessageSizeLimit(int messageSizeLimit) {
  }

  @Override
  public int getBinaryMessageSizeLimit() {
    return 0;
  }

  @Override
  public List<WebSocketExtension> getExtensions() {
    return List.of();
  }
}
