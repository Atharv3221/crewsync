package github.atharv3221.crewsync.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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
  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Column(unique = true)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public User(String name) {
    this.name = cleanName(name);
    this.createdAt = Instant.now();
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
