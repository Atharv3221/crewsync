package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.auth.JwtService;
import github.atharv3221.crewsync.document.DocMemberId;
import github.atharv3221.crewsync.document.DocMemberRepository;
import github.atharv3221.crewsync.document.DocumentRepository;
import github.atharv3221.crewsync.user.UserRepository;
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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Admits {@code /ws/{docId}?token=<jwt>} only for members of an existing document. Browsers can't
 * set headers on a WebSocket, so the token travels in the query string.
 */
@Component
@RequiredArgsConstructor
public class DocHandshakeInterceptor implements HandshakeInterceptor {

  static final String DOC_ID_ATTRIBUTE = "docId";
  static final String USER_ATTRIBUTE = "user";
  private static final String TOKEN_PARAMETER = "token";

  private final JwtService jwtService;
  private final DocumentRepository documentRepository;
  private final DocMemberRepository memberRepository;
  private final UserRepository userRepository;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Map<String, Object> attributes) {
    UriComponents uri = UriComponentsBuilder.fromUri(request.getURI()).build();
    Optional<UUID> docId = docIdOf(uri);
    Optional<SessionUser> user = userOf(uri);
    Optional<HttpStatus> rejection = rejectionFor(docId, user);
    rejection.ifPresentOrElse(response::setStatusCode, () -> {
      attributes.put(DOC_ID_ATTRIBUTE, docId.orElseThrow());
      attributes.put(USER_ATTRIBUTE, user.orElseThrow());
    });
    return rejection.isEmpty();
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Exception exception) {
  }

  private Optional<HttpStatus> rejectionFor(Optional<UUID> docId, Optional<SessionUser> user) {
    HttpStatus rejection = null;
    if (docId.isEmpty()) {
      rejection = HttpStatus.BAD_REQUEST;
    } else if (user.isEmpty()) {
      rejection = HttpStatus.UNAUTHORIZED;
    } else if (!documentRepository.existsById(docId.get())) {
      rejection = HttpStatus.NOT_FOUND;
    } else if (!memberRepository.existsById(new DocMemberId(docId.get(), user.get().id()))) {
      rejection = HttpStatus.FORBIDDEN;
    }
    return Optional.ofNullable(rejection);
  }

  private Optional<SessionUser> userOf(UriComponents uri) {
    return Optional.ofNullable(uri.getQueryParams().getFirst(TOKEN_PARAMETER))
        .flatMap(jwtService::findUserId)
        .flatMap(userRepository::findById)
        .map(found -> new SessionUser(found.getId(), found.getName()));
  }

  private static Optional<UUID> docIdOf(UriComponents uri) {
    List<String> segments = uri.getPathSegments();
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
