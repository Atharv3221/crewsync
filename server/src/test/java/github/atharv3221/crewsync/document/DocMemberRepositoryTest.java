package github.atharv3221.crewsync.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import github.atharv3221.crewsync.user.User;
import jakarta.persistence.Persistence;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class DocMemberRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private DocMemberRepository memberRepository;

  @Test
  void findsOnlyTheUsersMembershipsWithDocumentsLoaded() {
    User ada = entityManager.persist(new User("Ada"));
    User grace = entityManager.persist(new User("Grace"));
    Document owned = entityManager.persist(new Document("Owned", ada));
    Document shared = entityManager.persist(new Document("Shared", grace));
    Document foreign = entityManager.persist(new Document("Foreign", grace));
    entityManager.persist(new DocMember(owned, ada, DocRole.OWNER));
    entityManager.persist(new DocMember(shared, grace, DocRole.OWNER));
    entityManager.persist(new DocMember(shared, ada, DocRole.EDITOR));
    entityManager.persist(new DocMember(foreign, grace, DocRole.OWNER));
    entityManager.flush();
    entityManager.clear();

    List<DocMember> memberships = memberRepository.findAllByUserIdWithDocument(ada.getId());

    assertThat(memberships)
        .allSatisfy(member -> assertThat(Persistence.getPersistenceUtil()
            .isLoaded(member.getDocument())).isTrue())
        .extracting(member -> member.getDocument().getTitle(), DocMember::getRole)
        .containsExactlyInAnyOrder(tuple("Owned", DocRole.OWNER), tuple("Shared", DocRole.EDITOR));
  }

  @Test
  void returnsNothingForUserWithoutMemberships() {
    User loner = entityManager.persistAndFlush(new User("Loner"));

    assertThat(memberRepository.findAllByUserIdWithDocument(loner.getId())).isEmpty();
  }
}
