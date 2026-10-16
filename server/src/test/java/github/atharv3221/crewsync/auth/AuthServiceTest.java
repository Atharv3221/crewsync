package github.atharv3221.crewsync.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.atharv3221.crewsync.auth.AuthDtos.AuthResponse;
import github.atharv3221.crewsync.common.ConflictException;
import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;

@DataJpaTest
@Import({AuthService.class, JwtService.class, AuthConfig.class})
class AuthServiceTest {

  private static final String PASSWORD = "correct horse";

  @Autowired
  private AuthService authService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  @Test
  void signupStoresAHashAndReturnsAToken() {
    AuthResponse response = authService.signup("Ada", "ada@example.com", PASSWORD);

    User stored = userRepository.findById(response.user().id()).orElseThrow();
    assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(jwtService.parse(response.token())).isEqualTo(stored.getId());
    assertThat(response.user().email()).isEqualTo("ada@example.com");
  }

  @Test
  void signupNormalizesEmail() {
    AuthResponse response = authService.signup("Ada", "  Ada@Example.COM ", PASSWORD);

    assertThat(response.user().email()).isEqualTo("ada@example.com");
  }

  @Test
  void duplicateEmailConflictsRegardlessOfCase() {
    authService.signup("Ada", "ada@example.com", PASSWORD);

    assertThatThrownBy(() -> authService.signup("Other", "ADA@example.com ", PASSWORD))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void signupRejectsShortPasswords() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> authService.signup("Ada", "ada@example.com", "1234567"))
        .withMessageContaining("at least 8");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> authService.signup("Ada", "ada@example.com", null));
  }

  @Test
  void signupRejectsInvalidEmail() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> authService.signup("Ada", "not-an-email", PASSWORD));
  }

  @Test
  void loginWithCorrectPasswordReturnsAToken() {
    AuthResponse signedUp = authService.signup("Ada", "ada@example.com", PASSWORD);

    AuthResponse loggedIn = authService.login(" ADA@example.com", PASSWORD);

    assertThat(loggedIn.user()).isEqualTo(signedUp.user());
    assertThat(jwtService.parse(loggedIn.token())).isEqualTo(signedUp.user().id());
  }

  @Test
  void wrongPasswordAndUnknownEmailFailTheSameWay() {
    authService.signup("Ada", "ada@example.com", PASSWORD);

    assertThatThrownBy(() -> authService.login("ada@example.com", "wrong password"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password");
    assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password");
    assertThatThrownBy(() -> authService.login(null, null))
        .isInstanceOf(BadCredentialsException.class);
  }
}
