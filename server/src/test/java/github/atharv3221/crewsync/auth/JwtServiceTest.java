package github.atharv3221.crewsync.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;

class JwtServiceTest {

  private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1mb3ItY3Jld3JpdGUtand0LXRlc3RzLTEyMzQ1Njc4";
  private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hbHNvLTMyLWJ5dGVzLWxvbmc=";
  private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
  private static final Duration TTL = Duration.ofDays(7);

  private final UUID userId = UUID.randomUUID();
  private final JwtService jwtService = service(SECRET, NOW);

  @Test
  void roundTripsTheUserId() {
    assertThat(jwtService.parse(jwtService.issue(userId))).isEqualTo(userId);
  }

  @Test
  void acceptsTokenJustBeforeExpiry() {
    String token = jwtService.issue(userId);

    assertThat(service(SECRET, NOW.plus(TTL).minusSeconds(1)).parse(token)).isEqualTo(userId);
  }

  @Test
  void rejectsExpiredToken() {
    String token = jwtService.issue(userId);

    assertThatThrownBy(() -> service(SECRET, NOW.plus(TTL).plusSeconds(1)).parse(token))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void rejectsTamperedPayload() {
    String[] parts = jwtService.issue(userId).split("\\.");
    String otherPayload = service(SECRET, NOW).issue(UUID.randomUUID()).split("\\.")[1];
    String tampered = parts[0] + "." + otherPayload + "." + parts[2];

    assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void rejectsTokenSignedWithAnotherSecret() {
    String foreign = service(OTHER_SECRET, NOW).issue(userId);

    assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(BadCredentialsException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"garbage", "a.b.c", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."})
  void rejectsMalformedTokens(String token) {
    assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(BadCredentialsException.class);
    assertThat(jwtService.findUserId(token)).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"c2hvcnQ=", "not base64 !!"})
  void failsFastOnMissingOrWeakSecret(String secret) {
    assertThatIllegalStateException()
        .isThrownBy(() -> service(secret, NOW))
        .withMessageContaining("JWT_SECRET_CREWSYNC");
  }

  private static JwtService service(String secret, Instant now) {
    return new JwtService(new JwtProperties(secret, TTL), Clock.fixed(now, ZoneOffset.UTC));
  }
}
