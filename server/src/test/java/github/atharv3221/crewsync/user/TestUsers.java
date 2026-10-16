package github.atharv3221.crewsync.user;

import java.util.Locale;

public final class TestUsers {

  private TestUsers() {
  }

  /** A valid, unsaved user whose email is derived from the name. */
  public static User user(String name) {
    return new User(name, name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com", "hash");
  }
}
