package github.atharv3221.crewsync.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

  @Test
  void cleansNameByTrimmingAndCollapsingWhitespace() {
    User user = new User("  Ada \t  Lovelace\n");

    assertThat(user.getName()).isEqualTo("Ada Lovelace");
    assertThat(user.getCreatedAt()).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void rejectsBlankNames(String name) {
    assertThatIllegalArgumentException().isThrownBy(() -> new User(name));
  }

  @Test
  void rejectsNamesLongerThanTheLimit() {
    String tooLong = "a".repeat(User.MAX_NAME_LENGTH + 1);

    assertThatIllegalArgumentException().isThrownBy(() -> new User(tooLong));
  }

  @Test
  void acceptsNameAtTheLimit() {
    String atLimit = "a".repeat(User.MAX_NAME_LENGTH);

    assertThat(new User(atLimit).getName()).hasSize(User.MAX_NAME_LENGTH);
  }

  @Test
  void renameAppliesTheSameCleaning() {
    User user = new User("Ada");

    user.rename("  Grace   Hopper ");

    assertThat(user.getName()).isEqualTo("Grace Hopper");
  }

  @Test
  void renameRejectsBlankAndKeepsOldName() {
    User user = new User("Ada");

    assertThatIllegalArgumentException().isThrownBy(() -> user.rename(" "));
    assertThat(user.getName()).isEqualTo("Ada");
  }

  @Test
  void usersWithTheSameIdAreEqual() {
    UUID id = UUID.randomUUID();
    User first = withId(new User("Ada"), id);
    User second = withId(new User("Grace"), id);

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void usersWithDifferentIdsAreNotEqual() {
    User first = withId(new User("Ada"), UUID.randomUUID());
    User second = withId(new User("Ada"), UUID.randomUUID());

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void unsavedUsersAreOnlyEqualToThemselves() {
    User first = new User("Ada");
    User second = new User("Ada");

    assertThat(first).isEqualTo(first).isNotEqualTo(second).isNotEqualTo(null);
  }

  @Test
  void hashCodeIsStableAcrossIdAssignment() {
    User user = new User("Ada");
    int before = user.hashCode();

    withId(user, UUID.randomUUID());

    assertThat(user.hashCode()).isEqualTo(before);
  }

  private static User withId(User user, UUID id) {
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
