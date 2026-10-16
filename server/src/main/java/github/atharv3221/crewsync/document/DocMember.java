package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "doc_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocMember {

  @EmbeddedId
  private DocMemberId id;

  @MapsId("docId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "doc_id")
  private Document document;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DocRole role;

  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt;

  public DocMember(Document document, User user, DocRole role) {
    this.id = new DocMemberId(requirePersisted(document).getId(), requirePersisted(user).getId());
    this.document = document;
    this.user = user;
    this.role = Objects.requireNonNull(role, "role");
    this.addedAt = Instant.now();
  }

  private static Document requirePersisted(Document document) {
    if (document.getId() == null) {
      throw new IllegalArgumentException("Document must be saved before adding members");
    }
    return document;
  }

  private static User requirePersisted(User user) {
    if (user.getId() == null) {
      throw new IllegalArgumentException("User must be saved before joining a document");
    }
    return user;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof DocMember member && getId() != null && getId().equals(member.getId());
  }

  @Override
  public int hashCode() {
    return DocMember.class.hashCode();
  }
}
