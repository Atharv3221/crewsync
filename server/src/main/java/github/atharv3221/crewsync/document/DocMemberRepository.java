package github.atharv3221.crewsync.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocMemberRepository extends JpaRepository<DocMember, DocMemberId> {

  @Query("""
      select m from DocMember m
      join fetch m.document
      where m.id.userId = :userId
      order by m.addedAt desc
      """)
  List<DocMember> findAllByUserIdWithDocument(UUID userId);
}
