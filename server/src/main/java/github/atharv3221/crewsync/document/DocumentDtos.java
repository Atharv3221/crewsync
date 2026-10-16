package github.atharv3221.crewsync.document;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

  private DocumentDtos() {
  }

  public record CreateDocumentRequest(String title, UUID creatorId) {
  }

  public record DocumentResponse(UUID id, String title, Instant createdAt) {

    public static DocumentResponse from(Document document) {
      return new DocumentResponse(document.getId(), document.getTitle(), document.getCreatedAt());
    }
  }

  public record MemberDocumentResponse(
      UUID id, String title, Instant createdAt, DocRole role, Instant addedAt) {

    public static MemberDocumentResponse from(DocMember member) {
      Document document = member.getDocument();
      return new MemberDocumentResponse(
          document.getId(), document.getTitle(), document.getCreatedAt(),
          member.getRole(), member.getAddedAt());
    }
  }
}
