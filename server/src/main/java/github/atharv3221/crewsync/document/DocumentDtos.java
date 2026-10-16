package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.user.User;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

  private DocumentDtos() {
  }

  public record CreateDocumentRequest(String title) {
  }

  public record RenameDocumentRequest(String title) {
  }

  public record InviteMemberRequest(String email) {
  }

  /** A document as seen by one member, with that member's role. */
  public record DocumentResponse(
      UUID id, String title, long titleVersion, DocRole role, Instant createdAt) {

    public static DocumentResponse from(DocMember member) {
      Document document = member.getDocument();
      return new DocumentResponse(
          document.getId(), document.getTitle(), document.getTitleVersion(), member.getRole(),
          document.getCreatedAt());
    }
  }

  public record MemberResponse(UUID userId, String name, String email, DocRole role) {

    public static MemberResponse from(DocMember member) {
      User user = member.getUser();
      return new MemberResponse(user.getId(), user.getName(), user.getEmail(), member.getRole());
    }
  }
}
