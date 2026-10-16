package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.common.ConflictException;
import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.document.DocumentDtos.DocumentResponse;
import github.atharv3221.crewsync.document.DocumentDtos.MemberResponse;
import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents and their members. Every method acts on behalf of {@code userId}, the authenticated
 * caller: a missing document is 404, a document the caller isn't a member of is 403.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final DocMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public DocumentResponse create(String title, UUID userId) {
    User creator = findUser(userId);
    Document document = documentRepository.save(new Document(title, creator));
    DocMember owner = new DocMember(document, creator, DocRole.OWNER);
    memberRepository.save(owner);
    return DocumentResponse.from(owner);
  }

  @Transactional(readOnly = true)
  public DocumentResponse get(UUID docId, UUID userId) {
    return DocumentResponse.from(requireMember(docId, userId));
  }

  /**
   * Only the owner may rename; open editors are told after the change commits. The row lock makes
   * concurrent renames (say, from two of the owner's tabs) take turns, so each gets a distinct,
   * increasing title version.
   */
  @Transactional
  public DocumentResponse rename(UUID docId, UUID userId, String title) {
    Document document = documentRepository.findByIdForUpdate(docId)
        .orElseThrow(() -> new NotFoundException("Document", docId));
    DocMember member = requireMember(docId, userId);
    if (!member.isOwner()) {
      throw new AccessDeniedException("Only the owner can rename this document");
    }
    document.rename(title);
    events.publishEvent(
        new DocumentRenamedEvent(docId, document.getTitle(), document.getTitleVersion()));
    return DocumentResponse.from(member);
  }

  @Transactional(readOnly = true)
  public List<DocumentResponse> documentsOf(UUID userId) {
    findUser(userId);
    return memberRepository.findAllByUserIdWithDocument(userId).stream()
        .map(DocumentResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> members(UUID docId, UUID userId) {
    requireMember(docId, userId);
    return memberRepository.findAllByDocIdWithUser(docId).stream()
        .map(MemberResponse::from)
        .toList();
  }

  @Transactional
  public MemberResponse invite(UUID docId, UUID userId, String email) {
    Document document = requireOwner(docId, userId).getDocument();
    User invitee = userRepository.findByEmail(User.normalizeEmail(email))
        .orElseThrow(() -> new NotFoundException("No user with that email"));
    if (memberRepository.existsById(new DocMemberId(docId, invitee.getId()))) {
      throw new ConflictException(invitee.getName() + " is already a member");
    }
    DocMember member = new DocMember(document, invitee, DocRole.EDITOR);
    memberRepository.save(member);
    return MemberResponse.from(member);
  }

  @Transactional
  public void removeMember(UUID docId, UUID userId, UUID memberUserId) {
    requireOwner(docId, userId);
    if (userId.equals(memberUserId)) {
      throw new IllegalArgumentException("The owner can't remove themselves");
    }
    DocMember member = memberRepository.findById(new DocMemberId(docId, memberUserId))
        .orElseThrow(() -> new NotFoundException("That user is not a member"));
    memberRepository.delete(member);
    events.publishEvent(new MemberRemovedEvent(docId, memberUserId));
  }

  private DocMember requireMember(UUID docId, UUID userId) {
    if (!documentRepository.existsById(docId)) {
      throw new NotFoundException("Document", docId);
    }
    return memberRepository.findWithDocument(docId, userId)
        .orElseThrow(() -> new AccessDeniedException("You don't have access to this document"));
  }

  private DocMember requireOwner(UUID docId, UUID userId) {
    DocMember member = requireMember(docId, userId);
    if (!member.isOwner()) {
      throw new AccessDeniedException("Only the owner can manage members");
    }
    return member;
  }

  private User findUser(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
  }
}
