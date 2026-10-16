package github.atharv3221.crewsync.collab;

import java.util.List;
import java.util.UUID;

public interface OpLog {

  long maxSeq(UUID docId);

  List<SequencedOp> opsAfter(UUID docId, long seq);

  /**
   * Stores {@code ops} in one transaction. New ops get consecutive seqs starting at
   * {@code firstSeq}, in list order; an op whose key is already stored, or appears earlier in the
   * list, is not stored again and uses no seq.
   *
   * @return one entry per input op, in input order: the op as stored, so a duplicate comes back
   *     with its original seq
   */
  List<SequencedOp> appendAll(UUID docId, long firstSeq, List<ClientOp> ops);

  /** {@link #appendAll} for a single op. */
  default SequencedOp append(UUID docId, long seq, ClientOp op) {
    return appendAll(docId, seq, List.of(op)).getFirst();
  }
}
