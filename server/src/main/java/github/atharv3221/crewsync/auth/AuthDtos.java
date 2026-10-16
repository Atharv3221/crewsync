package github.atharv3221.crewsync.auth;

import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserDtos.UserResponse;

public final class AuthDtos {

  private AuthDtos() {
  }

  public record SignupRequest(String name, String email, String password) {
  }

  public record LoginRequest(String email, String password) {
  }

  public record AuthResponse(String token, UserResponse user) {

    public static AuthResponse from(String token, User user) {
      return new AuthResponse(token, UserResponse.from(user));
    }
  }
}
