package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.document.DocumentDtos.CreateDocumentRequest;
import github.atharv3221.crewsync.document.DocumentDtos.DocumentResponse;
import github.atharv3221.crewsync.document.DocumentDtos.InviteMemberRequest;
import github.atharv3221.crewsync.document.DocumentDtos.MemberResponse;
import github.atharv3221.crewsync.document.DocumentDtos.RenameDocumentRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping
  public ResponseEntity<DocumentResponse> create(@AuthenticationPrincipal UUID userId,
      @RequestBody CreateDocumentRequest request) {
    DocumentResponse created = documentService.create(request.title(), userId);
    return ResponseEntity.created(URI.create("/api/docs/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public DocumentResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return documentService.get(id, userId);
  }

  @PatchMapping("/{id}")
  public DocumentResponse rename(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
      @RequestBody RenameDocumentRequest request) {
    return documentService.rename(id, userId, request.title());
  }

  @GetMapping("/{id}/members")
  public List<MemberResponse> members(@AuthenticationPrincipal UUID userId,
      @PathVariable UUID id) {
    return documentService.members(id, userId);
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse invite(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
      @RequestBody InviteMemberRequest request) {
    return documentService.invite(id, userId, request.email());
  }

  @DeleteMapping("/{id}/members/{memberUserId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMember(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
      @PathVariable UUID memberUserId) {
    documentService.removeMember(id, userId, memberUserId);
  }
}
