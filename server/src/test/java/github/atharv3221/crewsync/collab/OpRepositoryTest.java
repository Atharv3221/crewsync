package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.atharv3221.crewsync.document.Document;
import github.atharv3221.crewsync.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class OpRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private OpRepository opRepository;

  private Document document;
  private Document otherDocument;

  @BeforeEach
  void setUp() {
    User user = entityManager.persist(user("Ada"));
    document = entityManager.persist(new Document("Notes", user));
    otherDocument = entityManager.persist(new Document("Other", user));
  }

  @Test
  void findsOpsAfterSeqInSeqOrder() {
    opRepository.save(new Op(document, 3, "insert:3@a", "{}"));
    opRepository.save(new Op(document, 1, "insert:1@a", "{}"));
    opRepository.save(new Op(document, 2, "insert:2@a", "{}"));
    opRepository.save(new Op(otherDocument, 4, "insert:4@a", "{}"));
    entityManager.flush();

    assertThat(opRepository.findAllByDocumentIdAndSeqGreaterThanOrderBySeqAsc(document.getId(), 1))
        .extracting(Op::getSeq)
        .containsExactly(2L, 3L);
  }

  @Test
  void maxSeqIsZeroForEmptyLogAndHighestSeqOtherwise() {
    assertThat(opRepository.findMaxSeq(document.getId())).isZero();

    opRepository.save(new Op(document, 1, "insert:1@a", "{}"));
    opRepository.save(new Op(document, 2, "insert:2@a", "{}"));
    opRepository.save(new Op(otherDocument, 9, "insert:9@a", "{}"));

    assertThat(opRepository.findMaxSeq(document.getId())).isEqualTo(2);
  }

  @Test
  void opKeyIsUniquePerDocument() {
    opRepository.saveAndFlush(new Op(document, 1, "insert:1@a", "{}"));

    assertThatThrownBy(() -> opRepository.saveAndFlush(new Op(document, 2, "insert:1@a", "{}")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void sameOpKeyIsAllowedInAnotherDocument() {
    opRepository.saveAndFlush(new Op(document, 1, "insert:1@a", "{}"));
    opRepository.saveAndFlush(new Op(otherDocument, 1, "insert:1@a", "{}"));

    assertThat(opRepository.findByDocumentIdAndOpKey(otherDocument.getId(), "insert:1@a")).isPresent();
  }

  @Test
  void seqIsUniquePerDocument() {
    opRepository.saveAndFlush(new Op(document, 1, "insert:1@a", "{}"));

    assertThatThrownBy(() -> opRepository.saveAndFlush(new Op(document, 1, "insert:2@a", "{}")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
