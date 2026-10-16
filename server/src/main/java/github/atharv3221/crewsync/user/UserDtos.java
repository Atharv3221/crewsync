package github.atharv3221.crewsync.user;

import java.util.UUID;

public final class UserDtos {

  private UserDtos() {
  }

  public record RenameUserRequest(String name) {
  }

  public record UserResponse(UUID id, String name, String email) {

    public static UserResponse from(User user) {
      return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
  }
}
