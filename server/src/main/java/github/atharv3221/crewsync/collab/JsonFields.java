package github.atharv3221.crewsync.collab;

import tools.jackson.databind.JsonNode;

final class JsonFields {

  private JsonFields() {
  }

  static JsonNode requireObject(JsonNode node, String name) {
    if (node == null || !node.isObject()) {
      throw new ProtocolException("'" + name + "' must be an object");
    }
    return node;
  }

  static String requireString(JsonNode parent, String field, int maxLength) {
    JsonNode value = parent.path(field);
    if (!value.isString() || value.stringValue().isBlank()
        || value.stringValue().length() > maxLength) {
      throw new ProtocolException(
          "'" + field + "' must be a non-blank string of at most " + maxLength + " characters");
    }
    return value.stringValue();
  }

  static long requireNonNegativeLong(JsonNode parent, String field) {
    JsonNode value = parent.path(field);
    if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
      throw new ProtocolException("'" + field + "' must be a non-negative integer");
    }
    return value.longValue();
  }
}
