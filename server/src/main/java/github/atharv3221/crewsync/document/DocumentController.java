package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.document.DocumentDtos.CreateDocumentRequest;
import github.atharv3221.crewsync.document.DocumentDtos.DocumentResponse;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping
  public ResponseEntity<DocumentResponse> create(@RequestBody CreateDocumentRequest request) {
    DocumentResponse created =
        DocumentResponse.from(documentService.create(request.title(), request.creatorId()));
    return ResponseEntity.created(URI.create("/api/docs/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public DocumentResponse get(@PathVariable UUID id) {
    return DocumentResponse.from(documentService.get(id));
  }
}
