package github.atharv3221.crewsync.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocMemberTest {

  private final UUID docId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final User user = withId(new User("Ada"), userId);
  private final Document document = withId(new Document("Notes", user), docId);

  @Test
  void compositeKeyIsBuiltFromDocumentAndUserIds() {
    DocMember member = new DocMember(document, user, DocRole.OWNER);

    assertThat(member.getId()).isEqualTo(new DocMemberId(docId, userId));
    assertThat(member.getId().getDocId()).isEqualTo(docId);
    assertThat(member.getId().getUserId()).isEqualTo(userId);
    assertThat(member.getRole()).isEqualTo(DocRole.OWNER);
    assertThat(member.getAddedAt()).isNotNull();
  }

  @Test
  void compositeKeyEqualityUsesBothParts() {
    DocMemberId id = new DocMemberId(docId, userId);

    assertThat(id).isEqualTo(new DocMemberId(docId, userId))
        .hasSameHashCodeAs(new DocMemberId(docId, userId))
        .isNotEqualTo(new DocMemberId(docId, UUID.randomUUID()))
        .isNotEqualTo(new DocMemberId(UUID.randomUUID(), userId));
  }

  @Test
  void membersWithTheSameKeyAreEqual() {
    DocMember owner = new DocMember(document, user, DocRole.OWNER);
    DocMember editor = new DocMember(document, user, DocRole.EDITOR);

    assertThat(owner).isEqualTo(editor).hasSameHashCodeAs(editor);
  }

  @Test
  void membersOfDifferentDocumentsAreNotEqual() {
    Document otherDocument = withId(new Document("Other", user), UUID.randomUUID());

    assertThat(new DocMember(document, user, DocRole.OWNER))
        .isNotEqualTo(new DocMember(otherDocument, user, DocRole.OWNER));
  }

  @Test
  void requiresSavedDocumentAndUser() {
    Document unsavedDocument = new Document("Draft", user);
    User unsavedUser = new User("Grace");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DocMember(unsavedDocument, user, DocRole.OWNER));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DocMember(document, unsavedUser, DocRole.EDITOR));
  }

  private static <T> T withId(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
