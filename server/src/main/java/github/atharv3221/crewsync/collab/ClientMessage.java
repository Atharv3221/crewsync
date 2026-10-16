package github.atharv3221.crewsync.collab;

import tools.jackson.databind.node.ObjectNode;

public sealed interface ClientMessage {

  record Hello(long lastSeq) implements ClientMessage {
  }

  record Submit(ClientOp op) implements ClientMessage {
  }

  record Presence(ObjectNode message) implements ClientMessage {
  }
}
