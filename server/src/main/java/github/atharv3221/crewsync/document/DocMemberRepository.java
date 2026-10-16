package github.atharv3221.crewsync.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocMemberRepository extends JpaRepository<DocMember, DocMemberId> {

  @Query("""
      select m from DocMember m
      join fetch m.document d
      where m.id.userId = :userId
      order by d.createdAt desc
      """)
  List<DocMember> findAllByUserIdWithDocument(UUID userId);

  @Query("""
      select m from DocMember m
      join fetch m.user
      where m.id.docId = :docId
      order by m.addedAt
      """)
  List<DocMember> findAllByDocIdWithUser(UUID docId);

  @Query("""
      select m from DocMember m
      join fetch m.document
      where m.id.docId = :docId and m.id.userId = :userId
      """)
  Optional<DocMember> findWithDocument(UUID docId, UUID userId);
}
