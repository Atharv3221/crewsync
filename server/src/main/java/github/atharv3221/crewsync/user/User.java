package github.atharv3221.crewsync.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  static final int MAX_NAME_LENGTH = 100;
  static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
  private static final Pattern EMAIL_FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public User(String name, String email, String passwordHash) {
    this.name = cleanName(name);
    this.email = cleanEmail(email);
    this.passwordHash = requirePasswordHash(passwordHash);
    this.createdAt = Instant.now();
  }

  /** The form emails are stored in, so lookups match regardless of case or surrounding spaces. */
  public static String normalizeEmail(String raw) {
    return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
  }

  public void rename(String newName) {
    this.name = cleanName(newName);
  }

  private static String cleanName(String raw) {
    String cleaned = raw == null ? "" : WHITESPACE_RUN.matcher(raw.strip()).replaceAll(" ");
    if (cleaned.isEmpty()) {
      throw new IllegalArgumentException("Name must not be blank");
    }
    if (cleaned.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("Name must be at most " + MAX_NAME_LENGTH + " characters");
    }
    return cleaned;
  }

  private static String cleanEmail(String raw) {
    String normalized = normalizeEmail(raw);
    if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Enter a valid email address");
    }
    return normalized;
  }

  private static String requirePasswordHash(String passwordHash) {
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("Password hash is required");
    }
    return passwordHash;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof User user && getId() != null && getId().equals(user.getId());
  }

  @Override
  public int hashCode() {
    return User.class.hashCode();
  }
}
