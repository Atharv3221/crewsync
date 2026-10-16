package github.atharv3221.crewsync.auth;

import github.atharv3221.crewsync.auth.AuthDtos.AuthResponse;
import github.atharv3221.crewsync.common.ConflictException;
import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_PASSWORD_BYTES = 72;
  private static final String INVALID_CREDENTIALS = "Invalid email or password";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final String unknownUserHash;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.unknownUserHash = passwordEncoder.encode("unknown-user-placeholder");
  }

  @Transactional
  public AuthResponse signup(String name, String email, String password) {
    requireAcceptablePassword(password);
    if (userRepository.existsByEmail(User.normalizeEmail(email))) {
      throw new ConflictException("An account with that email already exists");
    }
    User user = userRepository.save(new User(name, email, passwordEncoder.encode(password)));
    return AuthResponse.from(jwtService.issue(user.getId()), user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(String email, String password) {
    Optional<User> user = userRepository.findByEmail(User.normalizeEmail(email));
    if (!passwordMatches(user, password)) {
      throw new BadCredentialsException(INVALID_CREDENTIALS);
    }
    return AuthResponse.from(jwtService.issue(user.get().getId()), user.get());
  }

  /** Always runs one hash comparison, so unknown emails take as long as wrong passwords. */
  private boolean passwordMatches(Optional<User> user, String password) {
    String hash = user.map(User::getPasswordHash).orElse(unknownUserHash);
    boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
    return user.isPresent() && matches;
  }

  private static void requireAcceptablePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new IllegalArgumentException("Password is too long");
    }
  }
}
