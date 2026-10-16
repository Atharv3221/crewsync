package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.document.DocumentRepository;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class DocHandshakeInterceptor implements HandshakeInterceptor {

  static final String DOC_ID_ATTRIBUTE = "docId";

  private final DocumentRepository documentRepository;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Map<String, Object> attributes) {
    Optional<UUID> docId = docIdOf(request.getURI());
    boolean accepted = docId.filter(documentRepository::existsById).isPresent();
    if (accepted) {
      attributes.put(DOC_ID_ATTRIBUTE, docId.get());
    } else {
      response.setStatusCode(docId.isPresent() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST);
    }
    return accepted;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Exception exception) {
  }

  private static Optional<UUID> docIdOf(URI uri) {
    List<String> segments = UriComponentsBuilder.fromUri(uri).build().getPathSegments();
    return segments.isEmpty() ? Optional.empty() : parseUuid(segments.getLast());
  }

  private static Optional<UUID> parseUuid(String value) {
    Optional<UUID> parsed;
    try {
      parsed = Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException exception) {
      parsed = Optional.empty();
    }
    return parsed;
  }
}
