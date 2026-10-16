package github.atharv3221.crewsync.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.document.DocumentDtos.MemberDocumentResponse;
import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(DocumentService.class)
class DocumentServiceTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private DocumentService documentService;

  @Test
  void creatorBecomesOwner() {
    User creator = entityManager.persistAndFlush(new User("Ada"));

    Document document = documentService.create("  Plans ", creator.getId());
    entityManager.flush();

    assertThat(document.getId()).isNotNull();
    assertThat(document.getTitle()).isEqualTo("Plans");
    DocMember owner = entityManager.find(DocMember.class,
        new DocMemberId(document.getId(), creator.getId()));
    assertThat(owner.getRole()).isEqualTo(DocRole.OWNER);
  }

  @Test
  void unknownCreatorIsNotFound() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> documentService.create("Plans", unknown))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(unknown.toString());
  }

  @Test
  void missingCreatorIsRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> documentService.create("Plans", null));
  }

  @Test
  void listsDocumentsOfAMember() {
    User creator = entityManager.persistAndFlush(new User("Ada"));
    Document document = documentService.create("Plans", creator.getId());
    entityManager.flush();
    entityManager.clear();

    assertThat(documentService.documentsOf(creator.getId()))
        .extracting(MemberDocumentResponse::id, MemberDocumentResponse::role)
        .containsExactly(tuple(document.getId(), DocRole.OWNER));
  }

  @Test
  void listingForUnknownUserIsNotFound() {
    assertThatThrownBy(() -> documentService.documentsOf(UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }
}
