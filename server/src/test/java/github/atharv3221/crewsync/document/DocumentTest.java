package github.atharv3221.crewsync.document;

import static github.atharv3221.crewsync.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import github.atharv3221.crewsync.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentTest {

  private final User creator = user("Ada");

  @Test
  void cleansTitle() {
    assertThat(new Document("  Meeting   notes ", creator).getTitle()).isEqualTo("Meeting notes");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void blankTitleFallsBackToDefault(String title) {
    assertThat(new Document(title, creator).getTitle()).isEqualTo(Document.DEFAULT_TITLE);
  }

  @Test
  void rejectsTitlesLongerThanTheLimit() {
    String tooLong = "t".repeat(Document.MAX_TITLE_LENGTH + 1);

    assertThatIllegalArgumentException().isThrownBy(() -> new Document(tooLong, creator));
  }

  @Test
  void renameCleansTitleAndFallsBackToDefault() {
    Document document = new Document("Notes", creator);

    document.rename("  Q4   plan ");
    assertThat(document.getTitle()).isEqualTo("Q4 plan");

    document.rename("   ");
    assertThat(document.getTitle()).isEqualTo(Document.DEFAULT_TITLE);
  }

  @Test
  void renameRejectsOverlongTitleAndKeepsTheOldOne() {
    Document document = new Document("Notes", creator);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> document.rename("t".repeat(Document.MAX_TITLE_LENGTH + 1)));
    assertThat(document.getTitle()).isEqualTo("Notes");
  }

  @Test
  void requiresCreator() {
    assertThatNullPointerException().isThrownBy(() -> new Document("Notes", null));
  }

  @Test
  void startsWithoutSnapshot() {
    Document document = new Document("Notes", creator);

    assertThat(document.getSnapshot()).isNull();
    assertThat(document.getSnapshotSeq()).isNull();
    assertThat(document.getCreatedBy()).isSameAs(creator);
  }

  @Test
  void equalityFollowsId() {
    UUID id = UUID.randomUUID();
    Document first = withId(new Document("A", creator), id);
    Document second = withId(new Document("B", creator), id);
    Document other = withId(new Document("A", creator), UUID.randomUUID());

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second).isNotEqualTo(other);
    assertThat(new Document("A", creator)).isNotEqualTo(new Document("A", creator));
  }

  private static Document withId(Document document, UUID id) {
    ReflectionTestUtils.setField(document, "id", id);
    return document;
  }
}
