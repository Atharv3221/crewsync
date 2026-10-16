package github.atharv3221.crewsync.document;

import static github.atharv3221.crewsync.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import github.atharv3221.crewsync.user.User;
import jakarta.persistence.Persistence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class DocMemberRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private DocMemberRepository memberRepository;

  private User ada;
  private User grace;
  private Document older;
  private Document newer;
  private Document foreign;

  @BeforeEach
  void setUp() {
    ada = entityManager.persist(user("Ada"));
    grace = entityManager.persist(user("Grace"));
    older = entityManager.persist(createdAt(new Document("Older", ada), "2026-01-01T00:00:00Z"));
    newer = entityManager.persist(createdAt(new Document("Newer", grace), "2026-02-01T00:00:00Z"));
    foreign = entityManager.persist(new Document("Foreign", grace));
    entityManager.persist(new DocMember(older, ada, DocRole.OWNER));
    entityManager.persist(new DocMember(newer, grace, DocRole.OWNER));
    entityManager.persist(new DocMember(newer, ada, DocRole.EDITOR));
    entityManager.persist(new DocMember(foreign, grace, DocRole.OWNER));
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void findsTheUsersDocumentsNewestFirstWithDocumentsLoaded() {
    List<DocMember> memberships = memberRepository.findAllByUserIdWithDocument(ada.getId());

    assertThat(memberships)
        .allSatisfy(member -> assertThat(Persistence.getPersistenceUtil()
            .isLoaded(member.getDocument())).isTrue())
        .extracting(member -> member.getDocument().getTitle(), DocMember::getRole)
        .containsExactly(tuple("Newer", DocRole.EDITOR), tuple("Older", DocRole.OWNER));
  }

  @Test
  void returnsNothingForUserWithoutMemberships() {
    User loner = entityManager.persistAndFlush(user("Loner"));

    assertThat(memberRepository.findAllByUserIdWithDocument(loner.getId())).isEmpty();
  }

  @Test
  void findsMembersOfADocumentWithUsersLoaded() {
    List<DocMember> members = memberRepository.findAllByDocIdWithUser(newer.getId());

    assertThat(members)
        .allSatisfy(member -> assertThat(Persistence.getPersistenceUtil()
            .isLoaded(member.getUser())).isTrue())
        .extracting(member -> member.getUser().getName())
        .containsExactlyInAnyOrder("Grace", "Ada");
  }

  @Test
  void findsOneMembershipWithItsDocument() {
    assertThat(memberRepository.findWithDocument(newer.getId(), ada.getId()))
        .hasValueSatisfying(member -> assertThat(member.getDocument().getTitle()).isEqualTo("Newer"));
    assertThat(memberRepository.findWithDocument(foreign.getId(), ada.getId())).isEmpty();
  }

  private static Document createdAt(Document document, String instant) {
    ReflectionTestUtils.setField(document, "createdAt", Instant.parse(instant));
    return document;
  }
}
