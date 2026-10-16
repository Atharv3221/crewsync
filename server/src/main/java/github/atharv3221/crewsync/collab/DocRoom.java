package github.atharv3221.crewsync.collab;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * All sessions editing one document.
 *
 * <p>Submitted ops are queued under the room's lock, which fixes their order. One writer per room
 * takes up to {@link Batching#maxOps} queued ops, or what arrived within {@link Batching#maxWait}
 * of the oldest, and stores them in one transaction. Seqs are numbered by that transaction, so a
 * duplicate never uses one up and a failed batch leaves no gap. Only after the commit are the new
 * ops broadcast, in seq order, to every joined session including the sender.
 *
 * <p>A duplicate op (same key as a stored one) is answered to its sender alone with the stored op
 * and its original seq, so an outbox resent after a reconnect still gets acknowledged.
 *
 * <p>Messages are sent outside the lock to a snapshot of the sessions, so a session closing itself
 * while being sent to can't disturb the iteration. A session whose send fails is closed; nobody
 * else is affected. The exception is init, sent under the lock so it can't interleave with
 * broadcasts: a joined session gets exactly the ops after its init's seq.
 */
@Slf4j
final class DocRoom {

  /** How the writer groups queued ops into one transaction. */
  record Batching(int maxOps, Duration maxWait) {
  }

  private record Pending(String sessionId, ClientOp op, long queuedAtNanos) {
  }

  private record Ack(WebSocketSession session, SequencedOp op) {
  }

  private final UUID docId;
  private final OpLog opLog;
  private final MessageCodec codec;
  private final Batching batching;
  private final Executor writer;
  private final Consumer<DocRoom> onWriterIdle;

  private final Map<String, WebSocketSession> sessions = new HashMap<>();
  private final Set<String> joined = new LinkedHashSet<>();
  private final ArrayDeque<Pending> queue = new ArrayDeque<>();
  /** Highest seq stored and broadcast. Only the writer advances it. */
  private long seq;
  private long titleVersion;
  private boolean writing;
  private boolean closed;

  DocRoom(UUID docId, OpLog opLog, MessageCodec codec, Batching batching, Executor writer,
      Consumer<DocRoom> onWriterIdle) {
    this.docId = docId;
    this.opLog = opLog;
    this.codec = codec;
    this.batching = batching;
    this.writer = writer;
    this.onWriterIdle = onWriterIdle;
    this.seq = opLog.maxSeq(docId);
  }

  UUID docId() {
    return docId;
  }

  /**
   * @return false when the room was already closed; the caller must open a new one
   */
  synchronized boolean attach(WebSocketSession session) {
    if (!closed) {
      sessions.put(session.getId(), session);
    }
    return !closed;
  }

  /**
   * @return true when the room is closed: no sessions and nothing left to store
   */
  synchronized boolean detach(String sessionId) {
    sessions.remove(sessionId);
    joined.remove(sessionId);
    return closeIfIdle();
  }

  /** Closes the room if it has no sessions and no ops waiting; returns whether it is closed. */
  synchronized boolean closeIfIdle() {
    if (sessions.isEmpty() && queue.isEmpty() && !writing) {
      closed = true;
    }
    return closed;
  }

  void hello(String sessionId, long lastSeq) {
    WebSocketSession failed = null;
    synchronized (this) {
      WebSocketSession session = sessions.get(sessionId);
      if (session != null) {
        List<SequencedOp> ops = opLog.opsAfter(docId, lastSeq).stream()
            .filter(op -> op.seq() <= seq)
            .toList();
        if (trySend(session, codec.init(seq, ops))) {
          joined.add(sessionId);
        } else {
          failed = session;
        }
      }
    }
    closeUnreliable(failed);
  }

  void submit(String sessionId, ClientOp op) {
    boolean startWriter;
    synchronized (this) {
      requireJoined(sessionId);
      queue.addLast(new Pending(sessionId, op, System.nanoTime()));
      notifyAll();
      startWriter = !writing;
      writing = true;
    }
    if (startWriter) {
      writer.execute(this::drain);
    }
  }

  /**
   * Removes every session of {@code userId} and tells the remaining sessions that user left.
   * The removed sessions are returned for the caller to close.
   */
  List<WebSocketSession> evict(UUID userId) {
    List<WebSocketSession> evicted;
    List<WebSocketSession> recipients;
    synchronized (this) {
      evicted = sessions.values().stream()
          .filter(session -> SessionUser.of(session).id().equals(userId))
          .toList();
      evicted.forEach(session -> {
        sessions.remove(session.getId());
        joined.remove(session.getId());
      });
      recipients = evicted.isEmpty() ? List.of() : recipients(null);
    }
    sendToAll(recipients, List.of(codec.leave(userId)));
    return evicted;
  }

  /**
   * Tells every joined session about a new title. Rename listeners can run out of order, so a
   * title older than one already sent is dropped.
   */
  void announceTitle(String title, long version) {
    List<WebSocketSession> recipients = List.of();
    synchronized (this) {
      if (version > titleVersion) {
        titleVersion = version;
        recipients = recipients(null);
      }
    }
    sendToAll(recipients, List.of(codec.title(title, version)));
  }

  void relayPresence(String sessionId, TextMessage presence) {
    List<WebSocketSession> recipients;
    synchronized (this) {
      requireJoined(sessionId);
      recipients = recipients(sessionId);
    }
    sendToAll(recipients, List.of(presence));
  }

  // ---------- writer ----------

  private void drain() {
    List<Pending> batch = nextBatch();
    while (!batch.isEmpty()) {
      try {
        flush(batch);
      } catch (RuntimeException exception) {
        log.error("Unexpected failure flushing ops for doc {}", docId, exception);
      }
      batch = nextBatch();
    }
    onWriterIdle.accept(this);
  }

  /** The next batch, waiting up to maxWait from its oldest op; empty means the writer stops. */
  private synchronized List<Pending> nextBatch() {
    List<Pending> batch = new ArrayList<>();
    if (queue.isEmpty()) {
      writing = false;
    } else {
      long deadline = queue.getFirst().queuedAtNanos() + batching.maxWait().toNanos();
      long remaining = deadline - System.nanoTime();
      while (queue.size() < batching.maxOps() && remaining > 0) {
        try {
          TimeUnit.NANOSECONDS.timedWait(this, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          break;
        }
        remaining = deadline - System.nanoTime();
      }
      while (!queue.isEmpty() && batch.size() < batching.maxOps()) {
        batch.add(queue.pollFirst());
      }
    }
    return batch;
  }

  private void flush(List<Pending> batch) {
    List<SequencedOp> stored = store(batch.stream().map(Pending::op).toList());
    if (stored == null) {
      closeSenders(batch);
      return;
    }
    List<SequencedOp> fresh = new ArrayList<>();
    List<Ack> duplicates = new ArrayList<>();
    List<WebSocketSession> recipients;
    synchronized (this) {
      for (int i = 0; i < batch.size(); i++) {
        SequencedOp op = stored.get(i);
        if (op.seq() > seq) {
          seq = op.seq();
          fresh.add(op);
        } else {
          WebSocketSession sender = sessions.get(batch.get(i).sessionId());
          if (sender != null) {
            duplicates.add(new Ack(sender, op));
          }
        }
      }
      recipients = recipients(null);
    }
    sendToAll(recipients, fresh.stream().map(codec::op).toList());
    for (Ack ack : duplicates) {
      if (!trySend(ack.session(), codec.op(ack.op()))) {
        closeUnreliable(ack.session());
      }
    }
  }

  /** Stores the batch, retrying once; null when both attempts failed. */
  private List<SequencedOp> store(List<ClientOp> ops) {
    long firstSeq;
    synchronized (this) {
      firstSeq = seq + 1;
    }
    List<SequencedOp> stored = null;
    for (int attempt = 1; attempt <= 2 && stored == null; attempt++) {
      try {
        stored = opLog.appendAll(docId, firstSeq, ops);
      } catch (RuntimeException exception) {
        log.warn("Storing {} ops for doc {} failed (attempt {})", ops.size(), docId, attempt,
            exception);
      }
    }
    return stored;
  }

  /**
   * The batch is lost, so its senders are disconnected: their clients reconnect and resend the
   * ops still in their outbox. No seq was used, so the order stays gap-free.
   */
  private void closeSenders(List<Pending> batch) {
    Set<WebSocketSession> senders = new LinkedHashSet<>();
    synchronized (this) {
      for (Pending pending : batch) {
        WebSocketSession sender = sessions.get(pending.sessionId());
        if (sender != null) {
          senders.add(sender);
        }
      }
    }
    for (WebSocketSession sender : senders) {
      try {
        sender.close(CloseStatus.SERVER_ERROR);
      } catch (IOException exception) {
        log.debug("Could not close session {} in doc {}", sender.getId(), docId, exception);
      }
    }
  }

  // ---------- sending ----------

  private void requireJoined(String sessionId) {
    if (!joined.contains(sessionId)) {
      throw new ProtocolException("Send hello first");
    }
  }

  /** Snapshot of the joined sessions, minus {@code excludedSessionId}. Call holding the lock. */
  private List<WebSocketSession> recipients(String excludedSessionId) {
    return joined.stream()
        .filter(id -> !id.equals(excludedSessionId))
        .map(sessions::get)
        .toList();
  }

  /**
   * Sends each message, in order, to every recipient. A recipient whose send fails gets nothing
   * more and is closed.
   *
   * @return the recipients that failed
   */
  private List<WebSocketSession> sendToAll(List<WebSocketSession> recipients,
      List<TextMessage> messages) {
    List<WebSocketSession> failed = new ArrayList<>();
    for (TextMessage message : messages) {
      for (WebSocketSession session : recipients) {
        if (!failed.contains(session) && !trySend(session, message)) {
          failed.add(session);
        }
      }
    }
    failed.forEach(DocRoom::closeUnreliable);
    return failed;
  }

  private boolean trySend(WebSocketSession session, TextMessage message) {
    boolean sent = true;
    if (session.isOpen()) {
      try {
        session.sendMessage(message);
      } catch (IOException | RuntimeException exception) {
        log.debug("Send to session {} in doc {} failed", session.getId(), docId, exception);
        sent = false;
      }
    }
    return sent;
  }

  private static void closeUnreliable(WebSocketSession session) {
    if (session != null && session.isOpen()) {
      try {
        session.close(CloseStatus.SESSION_NOT_RELIABLE);
      } catch (IOException exception) {
        log.debug("Could not close session {}", session.getId(), exception);
      }
    }
  }
}
