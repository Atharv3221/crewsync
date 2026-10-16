package github.atharv3221.crewsync.user;

import github.atharv3221.crewsync.document.DocumentDtos.DocumentResponse;
import github.atharv3221.crewsync.document.DocumentService;
import github.atharv3221.crewsync.user.UserDtos.RenameUserRequest;
import github.atharv3221.crewsync.user.UserDtos.UserResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

  private final UserService userService;
  private final DocumentService documentService;

  @GetMapping
  public UserResponse get(@AuthenticationPrincipal UUID userId) {
    return UserResponse.from(userService.get(userId));
  }

  @PatchMapping
  public UserResponse rename(@AuthenticationPrincipal UUID userId,
      @RequestBody RenameUserRequest request) {
    return UserResponse.from(userService.rename(userId, request.name()));
  }

  @GetMapping("/docs")
  public List<DocumentResponse> documents(@AuthenticationPrincipal UUID userId) {
    return documentService.documentsOf(userId);
  }
}
