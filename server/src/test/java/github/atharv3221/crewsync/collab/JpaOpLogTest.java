package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;

import github.atharv3221.crewsync.document.Document;
import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import(JpaOpLog.class)
class JpaOpLogTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JpaOpLog opLog;

  private UUID docId;

  @BeforeEach
  void setUp() {
    User user = entityManager.persist(user("Ada"));
    docId = entityManager.persistAndFlush(new Document("Notes", user)).getId();
  }

  @Test
  void appendsAndReplaysOps() {
    assertThat(opLog.append(docId, 1, insert(1))).isEqualTo(new SequencedOp(1, insert(1).json()));
    assertThat(opLog.append(docId, 2, insert(2))).isEqualTo(new SequencedOp(2, insert(2).json()));

    assertThat(opLog.maxSeq(docId)).isEqualTo(2);
    assertThat(opLog.opsAfter(docId, 1))
        .containsExactly(new SequencedOp(2, insert(2).json()));
  }

  @Test
  void duplicateOpKeyReturnsTheStoredOpWithItsOriginalSeq() {
    opLog.append(docId, 1, insert(1));

    assertThat(opLog.append(docId, 2, insert(1))).isEqualTo(new SequencedOp(1, insert(1).json()));
    assertThat(opLog.maxSeq(docId)).isEqualTo(1);
  }

  private static ClientOp insert(long counter) {
    return ClientOp.parse(JSON.readTree("""
        {"kind":"insert","counter":%d,"site":"s1","value":"x","origin":null}
        """.formatted(counter)));
  }
}
