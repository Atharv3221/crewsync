package github.atharv3221.crewsync.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocMemberId implements Serializable {

  @Column(name = "doc_id")
  private UUID docId;

  @Column(name = "user_id")
  private UUID userId;

  public DocMemberId(UUID docId, UUID userId) {
    this.docId = Objects.requireNonNull(docId, "docId");
    this.userId = Objects.requireNonNull(userId, "userId");
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof DocMemberId that
        && Objects.equals(docId, that.docId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(docId, userId);
  }
}
