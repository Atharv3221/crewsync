package github.atharv3221.crewsync.collab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class InMemoryOpLog implements OpLog {

  private final List<SequencedOp> ops = new ArrayList<>();
  private final Map<String, SequencedOp> keys = new HashMap<>();
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
  public SequencedOp append(UUID docId, long seq, ClientOp op) {
    SequencedOp stored = keys.get(op.key());
    if (stored == null) {
      stored = new SequencedOp(seq, op.json());
      keys.put(op.key(), stored);
      ops.add(stored);
    }
    return stored;
  }

  List<SequencedOp> stored() {
    return List.copyOf(ops);
  }

  int maxSeqLoads() {
    return maxSeqLoads;
  }
}
