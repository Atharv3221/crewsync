package github.atharv3221.crewsync.collab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class InMemoryOpLog implements OpLog {

  private final List<SequencedOp> ops = new ArrayList<>();
  private final Set<String> keys = new HashSet<>();
  private int maxSeqLoads;

  @Override
  public long maxSeq(UUID docId) {
    maxSeqLoads++;
    return ops.isEmpty() ? 0 : ops.getLast().seq();
  }

  @Override
  public List<SequencedOp> opsAfter(UUID docId, long seq) {
    return ops.stream().filter(op -> op.seq() > seq).toList();
  }

  @Override
  public boolean append(UUID docId, long seq, ClientOp op) {
    boolean fresh = keys.add(op.key());
    if (fresh) {
      ops.add(new SequencedOp(seq, op.json()));
    }
    return fresh;
  }

  List<SequencedOp> stored() {
    return List.copyOf(ops);
  }

  int maxSeqLoads() {
    return maxSeqLoads;
  }
}
