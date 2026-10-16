package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.JsonFields.requireNonNegativeLong;
import static github.atharv3221.crewsync.collab.JsonFields.requireObject;
import static github.atharv3221.crewsync.collab.JsonFields.requireString;

import github.atharv3221.crewsync.collab.ClientMessage.Hello;
import github.atharv3221.crewsync.collab.ClientMessage.Presence;
import github.atharv3221.crewsync.collab.ClientMessage.Submit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class MessageCodec {

  private static final int MAX_TYPE_LENGTH = 16;

  private final JsonMapper jsonMapper;

  public ClientMessage decode(String payload) {
    JsonNode message = requireObject(readTree(payload), "message");
    return switch (requireString(message, "type", MAX_TYPE_LENGTH)) {
      case "hello" -> new Hello(requireNonNegativeLong(message, "lastSeq"));
      case "op" -> new Submit(ClientOp.parse(message.get("op")));
      case "presence" -> new Presence((ObjectNode) message);
      default -> throw new ProtocolException("Unknown message type");
    };
  }

  public TextMessage init(long seq, List<SequencedOp> ops) {
    ObjectNode message = jsonMapper.createObjectNode().put("type", "init").put("seq", seq);
    ArrayNode entries = message.putArray("ops");
    ops.forEach(op -> entries.addObject().put("seq", op.seq()).set("op", readTree(op.opJson())));
    return new TextMessage(message.toString());
  }

  public TextMessage op(SequencedOp op) {
    ObjectNode message = jsonMapper.createObjectNode().put("type", "op").put("seq", op.seq());
    message.set("op", readTree(op.opJson()));
    return new TextMessage(message.toString());
  }

  /** The presence message with its identity fields replaced by the authenticated user's. */
  public TextMessage presence(ObjectNode message, SessionUser user) {
    ObjectNode stamped = message.deepCopy()
        .put("userId", user.id().toString())
        .put("name", user.name());
    return new TextMessage(stamped.toString());
  }

  /** Tells peers that every tab of {@code userId} is gone, so its presence can be dropped. */
  public TextMessage leave(UUID userId) {
    return new TextMessage(jsonMapper.createObjectNode()
        .put("type", "leave")
        .put("userId", userId.toString())
        .toString());
  }

  public TextMessage title(String title, long version) {
    return new TextMessage(jsonMapper.createObjectNode()
        .put("type", "title")
        .put("title", title)
        .put("version", version)
        .toString());
  }

  private JsonNode readTree(String json) {
    try {
      return jsonMapper.readTree(json);
    } catch (JacksonException exception) {
      throw new ProtocolException("Malformed JSON");
    }
  }
}
