package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.JsonFields.requireNonEmptyString;
import static github.atharv3221.crewsync.collab.JsonFields.requireNonNegativeLong;
import static github.atharv3221.crewsync.collab.JsonFields.requireObject;
import static github.atharv3221.crewsync.collab.JsonFields.requireString;

import tools.jackson.databind.JsonNode;

/**
 * A CRDT op as sent by a client. The body stays opaque; only what identifies the op is checked.
 */
public record ClientOp(String key, JsonNode body) {

  static final int MAX_SITE_LENGTH = 64;
  private static final int MAX_KIND_LENGTH = 16;
  private static final int MAX_VALUE_LENGTH = 16;

  public static ClientOp parse(JsonNode op) {
    requireObject(op, "op");
    String kind = requireString(op, "kind", MAX_KIND_LENGTH);
    String charKey = requireCharId(op);
    switch (kind) {
      case "insert" -> requireInsertFields(op);
      case "delete" -> requireCharId(requireObject(op.get("target"), "target"));
      default -> throw new ProtocolException("'kind' must be 'insert' or 'delete'");
    }
    return new ClientOp(kind + ":" + charKey, op);
  }

  public String json() {
    return body.toString();
  }

  private static void requireInsertFields(JsonNode op) {
    requireNonEmptyString(op, "value", MAX_VALUE_LENGTH);
    if (!op.path("origin").isNull()) {
      requireCharId(requireObject(op.get("origin"), "origin"));
    }
  }

  private static String requireCharId(JsonNode node) {
    long counter = requireNonNegativeLong(node, "counter");
    String site = requireString(node, "site", MAX_SITE_LENGTH);
    return counter + "@" + site;
  }
}
