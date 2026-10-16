package github.atharv3221.crewsync.collab;

import java.util.List;
import java.util.UUID;

public interface OpLog {

  long maxSeq(UUID docId);

  List<SequencedOp> opsAfter(UUID docId, long seq);

  /**
   * Stores {@code op} under {@code seq} unless an op with the same key was already stored.
   *
   * @return false when the op is a duplicate and nothing was stored
   */
  boolean append(UUID docId, long seq, ClientOp op);
}
