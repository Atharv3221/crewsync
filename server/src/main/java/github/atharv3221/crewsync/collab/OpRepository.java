package github.atharv3221.crewsync.collab;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OpRepository extends JpaRepository<Op, Long> {

  @Query("select coalesce(max(o.seq), 0) from Op o where o.document.id = :docId")
  long findMaxSeq(UUID docId);

  List<Op> findAllByDocumentIdAndSeqGreaterThanOrderBySeqAsc(UUID docId, long seq);

  Optional<Op> findByDocumentIdAndOpKey(UUID docId, String opKey);
}
