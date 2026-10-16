package github.atharv3221.crewsync.document;

import static github.atharv3221.crewsync.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import github.atharv3221.crewsync.common.ConflictException;
import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.document.DocumentDtos.DocumentResponse;
import github.atharv3221.crewsync.document.DocumentDtos.MemberResponse;
import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;

@DataJpaTest
@Import(DocumentService.class)
class DocumentServiceTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private DocumentService documentService;

  private User owner;
  private User editor;
  private User outsider;
  private UUID docId;

  @BeforeEach
  void setUp() {
    owner = entityManager.persist(user("Ada"));
    editor = entityManager.persist(user("Grace"));
    outsider = entityManager.persistAndFlush(user("Eve"));
    docId = documentService.create("  Plans ", owner.getId()).id();
    documentService.invite(docId, owner.getId(), editor.getEmail());
    flushAndClear();
  }

  @Test
  void creatorBecomesOwner() {
    DocumentResponse created = documentService.create(null, owner.getId());

    assertThat(created.title()).isEqualTo(Document.DEFAULT_TITLE);
    assertThat(created.role()).isEqualTo(DocRole.OWNER);
    assertThat(documentService.get(docId, owner.getId()).title()).isEqualTo("Plans");
  }

  @Test
  void unknownCreatorIsNotFound() {
    assertThatThrownBy(() -> documentService.create("Plans", UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void membersCanOpenTheDocumentWithTheirRole() {
    assertThat(documentService.get(docId, editor.getId()).role()).isEqualTo(DocRole.EDITOR);
  }

  @Test
  void nonMembersAreDenied() {
    assertThatThrownBy(() -> documentService.get(docId, outsider.getId()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> documentService.members(docId, outsider.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingDocumentIsNotFoundEvenForStrangers() {
    assertThatThrownBy(() -> documentService.get(UUID.randomUUID(), outsider.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void ownerCanRename() {
    DocumentResponse renamed = documentService.rename(docId, owner.getId(), " Roadmap ");
    flushAndClear();

    assertThat(renamed.title()).isEqualTo("Roadmap");
    assertThat(renamed.titleVersion()).isEqualTo(1);
    assertThat(renamed.role()).isEqualTo(DocRole.OWNER);
    assertThat(documentService.get(docId, editor.getId()).title()).isEqualTo("Roadmap");
  }

  @Test
  void editorsCannotRename() {
    assertThatThrownBy(() -> documentService.rename(docId, editor.getId(), "Mine now"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Only the owner can rename this document");
    flushAndClear();

    DocumentResponse doc = documentService.get(docId, owner.getId());
    assertThat(doc.title()).isNotEqualTo("Mine now");
    assertThat(doc.titleVersion()).isZero();
  }

  @Test
  void everyRenameBumpsTheTitleVersion() {
    assertThat(documentService.get(docId, owner.getId()).titleVersion()).isZero();

    documentService.rename(docId, owner.getId(), "One");
    flushAndClear();
    documentService.rename(docId, owner.getId(), "Two");
    flushAndClear();

    DocumentResponse doc = documentService.get(docId, owner.getId());
    assertThat(doc.title()).isEqualTo("Two");
    assertThat(doc.titleVersion()).isEqualTo(2);
  }

  @Test
  void nonMembersCannotRename() {
    assertThatThrownBy(() -> documentService.rename(docId, outsider.getId(), "Hijacked"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> documentService.rename(UUID.randomUUID(), owner.getId(), "Gone"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listsMembersInJoinOrder() {
    assertThat(documentService.members(docId, editor.getId()))
        .extracting(MemberResponse::name, MemberResponse::role)
        .containsExactly(tuple("Ada", DocRole.OWNER), tuple("Grace", DocRole.EDITOR));
  }

  @Test
  void ownerInvitesByEmailIgnoringCaseAndSpaces() {
    MemberResponse invited = documentService.invite(docId, owner.getId(), "  EVE@example.com ");

    assertThat(invited.userId()).isEqualTo(outsider.getId());
    assertThat(invited.role()).isEqualTo(DocRole.EDITOR);
    assertThat(documentService.documentsOf(outsider.getId()))
        .extracting(DocumentResponse::id).containsExactly(docId);
  }

  @Test
  void onlyTheOwnerCanInvite() {
    assertThatThrownBy(() -> documentService.invite(docId, editor.getId(), outsider.getEmail()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void invitingUnknownEmailIsNotFound() {
    assertThatThrownBy(() -> documentService.invite(docId, owner.getId(), "nobody@example.com"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("No user with that email");
  }

  @Test
  void invitingAnExistingMemberConflicts() {
    assertThatThrownBy(() -> documentService.invite(docId, owner.getId(), editor.getEmail()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void ownerRemovesAnEditor() {
    documentService.removeMember(docId, owner.getId(), editor.getId());
    flushAndClear();

    assertThatThrownBy(() -> documentService.get(docId, editor.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void ownerCannotRemoveThemselves() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> documentService.removeMember(docId, owner.getId(), owner.getId()));
  }

  @Test
  void editorsCannotRemoveMembers() {
    assertThatThrownBy(() -> documentService.removeMember(docId, editor.getId(), owner.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void removingANonMemberIsNotFound() {
    assertThatThrownBy(() -> documentService.removeMember(docId, owner.getId(), outsider.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listingForUnknownUserIsNotFound() {
    assertThatThrownBy(() -> documentService.documentsOf(UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
