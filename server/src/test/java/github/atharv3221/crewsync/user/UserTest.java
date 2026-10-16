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
    User user = newUser("  Ada \t  Lovelace\n");

    assertThat(user.getName()).isEqualTo("Ada Lovelace");
    assertThat(user.getCreatedAt()).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void rejectsBlankNames(String name) {
    assertThatIllegalArgumentException().isThrownBy(() -> newUser(name));
  }

  @Test
  void rejectsNamesLongerThanTheLimit() {
    String tooLong = "a".repeat(User.MAX_NAME_LENGTH + 1);

    assertThatIllegalArgumentException().isThrownBy(() -> newUser(tooLong));
  }

  @Test
  void acceptsNameAtTheLimit() {
    String atLimit = "a".repeat(User.MAX_NAME_LENGTH);

    assertThat(newUser(atLimit).getName()).hasSize(User.MAX_NAME_LENGTH);
  }

  @Test
  void renameAppliesTheSameCleaning() {
    User user = newUser("Ada");

    user.rename("  Grace   Hopper ");

    assertThat(user.getName()).isEqualTo("Grace Hopper");
  }

  @Test
  void renameRejectsBlankAndKeepsOldName() {
    User user = newUser("Ada");

    assertThatIllegalArgumentException().isThrownBy(() -> user.rename(" "));
    assertThat(user.getName()).isEqualTo("Ada");
  }

  @Test
  void usersWithTheSameIdAreEqual() {
    UUID id = UUID.randomUUID();
    User first = withId(newUser("Ada"), id);
    User second = withId(newUser("Grace"), id);

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void usersWithDifferentIdsAreNotEqual() {
    User first = withId(newUser("Ada"), UUID.randomUUID());
    User second = withId(newUser("Ada"), UUID.randomUUID());

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void unsavedUsersAreOnlyEqualToThemselves() {
    User first = newUser("Ada");
    User second = newUser("Ada");

    assertThat(first).isEqualTo(first).isNotEqualTo(second).isNotEqualTo(null);
  }

  @Test
  void hashCodeIsStableAcrossIdAssignment() {
    User user = newUser("Ada");
    int before = user.hashCode();

    withId(user, UUID.randomUUID());

    assertThat(user.hashCode()).isEqualTo(before);
  }

  @Test
  void normalizesEmail() {
    User user = new User("Ada", "  Ada.Lovelace@Example.COM ", "hash");

    assertThat(user.getEmail()).isEqualTo("ada.lovelace@example.com");
    assertThat(User.normalizeEmail(" A@B.Co ")).isEqualTo("a@b.co");
    assertThat(User.normalizeEmail(null)).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"ada", "ada@", "@example.com", "ada@example", "a da@example.com",
      "ada@@example.com"})
  void rejectsInvalidEmails(String email) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User("Ada", email, "hash"))
        .withMessage("Enter a valid email address");
  }

  @Test
  void rejectsOverlongEmail() {
    String email = "a".repeat(User.MAX_EMAIL_LENGTH) + "@example.com";

    assertThatIllegalArgumentException().isThrownBy(() -> new User("Ada", email, "hash"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void requiresPasswordHash(String hash) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User("Ada", "ada@example.com", hash));
  }

  private static User newUser(String name) {
    return new User(name, "ada@example.com", "hash");
  }

  private static User withId(User user, UUID id) {
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
