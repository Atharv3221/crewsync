package github.atharv3221.crewsync.collab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thread-safe, so concurrency tests can share it. Can be told to fail its next appends. */
class InMemoryOpLog implements OpLog {

  private final List<SequencedOp> ops = new ArrayList<>();
  private final Map<String, SequencedOp> keys = new HashMap<>();
  private final List<Integer> batchSizes = new ArrayList<>();
  private int maxSeqLoads;
  private int failuresLeft;

  @Override
  public synchronized long maxSeq(UUID docId) {
    maxSeqLoads++;
    return ops.isEmpty() ? 0 : ops.getLast().seq();
  }

  @Override
  public synchronized List<SequencedOp> opsAfter(UUID docId, long seq) {
    return ops.stream().filter(op -> op.seq() > seq).toList();
  }

  /** All or nothing, like the database transaction. */
  @Override
  public synchronized List<SequencedOp> appendAll(UUID docId, long firstSeq, List<ClientOp> batch) {
    if (failuresLeft > 0) {
      failuresLeft--;
      throw new IllegalStateException("simulated database failure");
    }
    batchSizes.add(batch.size());
    Map<String, SequencedOp> byKey = new HashMap<>(keys);
    List<SequencedOp> fresh = new ArrayList<>();
    List<SequencedOp> stored = new ArrayList<>();
    long seq = firstSeq;
    for (ClientOp op : batch) {
      SequencedOp existing = byKey.get(op.key());
      if (existing == null) {
        if (!ops.isEmpty() && seq <= ops.getLast().seq()) {
          throw new IllegalStateException("seq " + seq + " already used");
        }
        existing = new SequencedOp(seq++, op.json());
        byKey.put(op.key(), existing);
        fresh.add(existing);
      }
      stored.add(existing);
    }
    keys.putAll(byKey);
    ops.addAll(fresh);
    return stored;
  }

  synchronized void failNextAppends(int count) {
    failuresLeft = count;
  }

  synchronized List<SequencedOp> stored() {
    return List.copyOf(ops);
  }

  synchronized List<Integer> batchSizes() {
    return List.copyOf(batchSizes);
  }

  synchronized int maxSeqLoads() {
    return maxSeqLoads;
  }
}
