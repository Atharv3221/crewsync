package github.atharv3221.crewsync.document;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  /** Row-locks the document until the transaction ends, so concurrent renames run one at a time. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Document d where d.id = :id")
  Optional<Document> findByIdForUpdate(@Param("id") UUID id);
}
