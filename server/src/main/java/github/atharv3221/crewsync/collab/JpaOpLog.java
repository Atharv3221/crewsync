package github.atharv3221.crewsync.collab;

import github.atharv3221.crewsync.document.Document;
import github.atharv3221.crewsync.document.DocumentRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /** One select for the batch's keys, then the new ops, all committed together. */
  @Override
  @Transactional
  public List<SequencedOp> appendAll(UUID docId, long firstSeq, List<ClientOp> ops) {
    Map<String, SequencedOp> byKey = new HashMap<>();
    opRepository.findAllByDocumentIdAndOpKeyIn(docId, ops.stream().map(ClientOp::key).toList())
        .forEach(op -> byKey.put(op.getOpKey(), new SequencedOp(op.getSeq(), op.getOpJson())));
    Document document = documentRepository.getReferenceById(docId);
    List<Op> fresh = new ArrayList<>();
    List<SequencedOp> stored = new ArrayList<>(ops.size());
    long seq = firstSeq;
    for (ClientOp op : ops) {
      SequencedOp existing = byKey.get(op.key());
      if (existing == null) {
        existing = new SequencedOp(seq, op.json());
        fresh.add(new Op(document, seq, op.key(), op.json()));
        byKey.put(op.key(), existing);
        seq++;
      }
      stored.add(existing);
    }
    opRepository.saveAll(fresh);
    return stored;
  }
}
