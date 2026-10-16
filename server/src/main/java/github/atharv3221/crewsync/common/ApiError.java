package github.atharv3221.crewsync.common;

import org.springframework.http.HttpStatus;

public record ApiError(int status, String error, String message) {

  public static ApiError of(HttpStatus status, String message) {
    return new ApiError(status.value(), status.getReasonPhrase(), message);
  }
}
