package github.atharv3221.crewsync.document;

import github.atharv3221.crewsync.common.NotFoundException;
import github.atharv3221.crewsync.document.DocumentDtos.MemberDocumentResponse;
import github.atharv3221.crewsync.user.User;
import github.atharv3221.crewsync.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final DocMemberRepository memberRepository;
  private final UserRepository userRepository;

  @Transactional
  public Document create(String title, UUID creatorId) {
    User creator = findUser(requireCreatorId(creatorId));
    Document document = documentRepository.save(new Document(title, creator));
    memberRepository.save(new DocMember(document, creator, DocRole.OWNER));
    return document;
  }

  @Transactional(readOnly = true)
  public Document get(UUID id) {
    return documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document", id));
  }

  @Transactional(readOnly = true)
  public List<MemberDocumentResponse> documentsOf(UUID userId) {
    findUser(userId);
    return memberRepository.findAllByUserIdWithDocument(userId).stream()
        .map(MemberDocumentResponse::from)
        .toList();
  }

  private User findUser(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
  }

  private static UUID requireCreatorId(UUID creatorId) {
    if (creatorId == null) {
      throw new IllegalArgumentException("creatorId is required");
    }
    return creatorId;
  }
}
