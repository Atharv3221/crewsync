package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

  static final String DEFAULT_TITLE = "Untitled";
  static final int MAX_TITLE_LENGTH = 200;
  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  /** Bumped on every rename, so clients can ignore a title that arrives after a newer one. */
  @Column(name = "title_version", nullable = false,
      columnDefinition = "bigint default 0 not null")
  private long titleVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false, updatable = false)
  private User createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(columnDefinition = "bytea")
  private byte[] snapshot;

  @Column(name = "snapshot_seq")
  private Long snapshotSeq;

  public Document(String title, User createdBy) {
    this.title = cleanTitle(title);
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.createdAt = Instant.now();
  }

  public void rename(String newTitle) {
    this.title = cleanTitle(newTitle);
    this.titleVersion++;
  }

  private static String cleanTitle(String raw) {
    String cleaned = raw == null ? "" : WHITESPACE_RUN.matcher(raw.strip()).replaceAll(" ");
    if (cleaned.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException(
          "Title must be at most " + MAX_TITLE_LENGTH + " characters");
    }
    return cleaned.isEmpty() ? DEFAULT_TITLE : cleaned;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof Document document && getId() != null
        && getId().equals(document.getId());
  }

  @Override
  public int hashCode() {
    return Document.class.hashCode();
  }
}
