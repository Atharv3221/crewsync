package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.document.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ops", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ops_doc_seq", columnNames = {"doc_id", "seq"}),
    @UniqueConstraint(name = "uk_ops_doc_op_key", columnNames = {"doc_id", "op_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Op {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "doc_id", nullable = false, updatable = false)
  private Document document;

  @Column(nullable = false, updatable = false)
  private long seq;

  @Column(name = "op_key", nullable = false, updatable = false)
  private String opKey;

  @Column(name = "op_json", nullable = false, updatable = false, columnDefinition = "text")
  private String opJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Op(Document document, long seq, String opKey, String opJson) {
    this.document = Objects.requireNonNull(document, "document");
    this.seq = requirePositive(seq);
    this.opKey = requireText(opKey, "opKey");
    this.opJson = requireText(opJson, "opJson");
    this.createdAt = Instant.now();
  }

  private static long requirePositive(long seq) {
    if (seq <= 0) {
      throw new IllegalArgumentException("seq must be positive");
    }
    return seq;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof Op op && getId() != null && getId().equals(op.getId());
  }

  @Override
  public int hashCode() {
    return Op.class.hashCode();
  }
}
