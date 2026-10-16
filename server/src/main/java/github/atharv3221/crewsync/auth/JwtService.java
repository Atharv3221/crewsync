package github.atharv3221.crewsync.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/** Issues and verifies the HS256 tokens that identify a user. The subject is the user id. */
@Service
public class JwtService {

  private static final String INVALID_SECRET = "jwt.secret must be a base64-encoded key of at least"
      + " 32 bytes. Set JWT_SECRET_CREWSYNC, e.g. to the output of `openssl rand -base64 32`";

  private final SecretKey key;
  private final Duration ttl;
  private final Clock clock;
  private final JwtParser parser;

  public JwtService(JwtProperties properties, Clock clock) {
    this.key = signingKey(properties.secret());
    this.ttl = properties.ttl();
    this.clock = clock;
    this.parser = Jwts.parser()
        .verifyWith(key)
        .clock(() -> Date.from(clock.instant()))
        .build();
  }

  public String issue(UUID userId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * @throws BadCredentialsException when the token is expired, tampered with or malformed
   */
  public UUID parse(String token) {
    try {
      String subject = parser.parseSignedClaims(token).getPayload().getSubject();
      return UUID.fromString(Objects.requireNonNullElse(subject, ""));
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BadCredentialsException("Invalid or expired token", exception);
    }
  }

  public Optional<UUID> findUserId(String token) {
    Optional<UUID> userId;
    try {
      userId = Optional.of(parse(token));
    } catch (BadCredentialsException exception) {
      userId = Optional.empty();
    }
    return userId;
  }

  private static SecretKey signingKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(INVALID_SECRET);
    }
    try {
      return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    } catch (JwtException exception) {
      throw new IllegalStateException(INVALID_SECRET, exception);
    }
  }
}
