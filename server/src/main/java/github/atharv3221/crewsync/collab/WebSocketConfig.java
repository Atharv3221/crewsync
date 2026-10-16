package github.atharv3221.crewsync.collab;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final CollabHandler collabHandler;
  private final DocHandshakeInterceptor docHandshakeInterceptor;

  @Value("${crewsync.allowed-origin-patterns}")
  private String[] allowedOriginPatterns;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(collabHandler, "/ws/{docId}")
        .addInterceptors(docHandshakeInterceptor)
        .setAllowedOriginPatterns(allowedOriginPatterns);
  }
}
