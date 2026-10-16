package github.atharv3221.crewsync.user;

import github.atharv3221.crewsync.document.DocumentDtos.MemberDocumentResponse;
import github.atharv3221.crewsync.document.DocumentService;
import github.atharv3221.crewsync.user.UserDtos.CreateUserRequest;
import github.atharv3221.crewsync.user.UserDtos.RenameUserRequest;
import github.atharv3221.crewsync.user.UserDtos.UserResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final DocumentService documentService;

  @PostMapping
  public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
    UserResponse created = UserResponse.from(userService.create(request.name()));
    return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable UUID id) {
    return UserResponse.from(userService.get(id));
  }

  @PatchMapping("/{id}")
  public UserResponse rename(@PathVariable UUID id, @RequestBody RenameUserRequest request) {
    return UserResponse.from(userService.rename(id, request.name()));
  }

  @GetMapping("/{id}/docs")
  public List<MemberDocumentResponse> documents(@PathVariable UUID id) {
    return documentService.documentsOf(id);
  }
}
