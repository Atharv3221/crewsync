package github.atharv3221.crewsync.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a valid bearer token. An invalid token leaves the request
 * anonymous, so public endpoints still work and protected ones answer 401.
 */
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    bearerToken(request).flatMap(jwtService::findUserId).ifPresent(JwtAuthenticationFilter::signIn);
    chain.doFilter(request, response);
  }

  private static Optional<String> bearerToken(HttpServletRequest request) {
    return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
        .filter(header -> header.startsWith(BEARER_PREFIX))
        .map(header -> header.substring(BEARER_PREFIX.length()).strip());
  }

  private static void signIn(UUID userId) {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
  }
}
