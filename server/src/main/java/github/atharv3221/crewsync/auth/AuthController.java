package github.atharv3221.crewsync.auth;

import github.atharv3221.crewsync.auth.AuthDtos.AuthResponse;
import github.atharv3221.crewsync.auth.AuthDtos.LoginRequest;
import github.atharv3221.crewsync.auth.AuthDtos.SignupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@RequestBody SignupRequest request) {
    return authService.signup(request.name(), request.email(), request.password());
  }

  @PostMapping("/login")
  public AuthResponse login(@RequestBody LoginRequest request) {
    return authService.login(request.email(), request.password());
  }
}
