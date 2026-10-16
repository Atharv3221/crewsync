package github.atharv3221.crewsync.collab;

public sealed interface ClientMessage {

  record Hello(long lastSeq) implements ClientMessage {
  }

  record Submit(ClientOp op) implements ClientMessage {
  }

  record Presence(String payload) implements ClientMessage {
  }
}
