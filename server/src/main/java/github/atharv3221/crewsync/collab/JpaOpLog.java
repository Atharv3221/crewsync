package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.document.DocumentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JpaOpLog implements OpLog {

  private final OpRepository opRepository;
  private final DocumentRepository documentRepository;

  @Override
  @Transactional(readOnly = true)
  public long maxSeq(UUID docId) {
    return opRepository.findMaxSeq(docId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SequencedOp> opsAfter(UUID docId, long seq) {
    return opRepository.findAllByDocumentIdAndSeqGreaterThanOrderBySeqAsc(docId, seq).stream()
        .map(op -> new SequencedOp(op.getSeq(), op.getOpJson()))
        .toList();
  }

  @Override
  @Transactional
  public boolean append(UUID docId, long seq, ClientOp op) {
    boolean fresh = !opRepository.existsByDocumentIdAndOpKey(docId, op.key());
    if (fresh) {
      opRepository.save(new Op(documentRepository.getReferenceById(docId), seq, op.key(), op.json()));
    }
    return fresh;
  }
}
