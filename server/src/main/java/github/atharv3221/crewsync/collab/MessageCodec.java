package github.atharv3221.crewsync.collab;

import static github.atharv3221.crewsync.collab.JsonFields.requireNonNegativeLong;
import static github.atharv3221.crewsync.collab.JsonFields.requireObject;
import static github.atharv3221.crewsync.collab.JsonFields.requireString;

import github.atharv3221.crewsync.collab.ClientMessage.Hello;
import github.atharv3221.crewsync.collab.ClientMessage.Presence;
import github.atharv3221.crewsync.collab.ClientMessage.Submit;
import java.util.List;
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
      case "presence" -> new Presence(payload);
      default -> throw new ProtocolException("Unknown message type");
    };
  }

  public TextMessage init(long seq, List<SequencedOp> ops) {
    ObjectNode message = jsonMapper.createObjectNode().put("type", "init").put("seq", seq);
    ArrayNode entries = message.putArray("ops");
    ops.forEach(op -> entries.addObject().put("seq", op.seq()).set("op", readTree(op.opJson())));
    return new TextMessage(message.toString());
  }

  public TextMessage op(long seq, ClientOp op) {
    ObjectNode message = jsonMapper.createObjectNode().put("type", "op").put("seq", seq);
    message.set("op", op.body());
    return new TextMessage(message.toString());
  }

  private JsonNode readTree(String json) {
    try {
      return jsonMapper.readTree(json);
    } catch (JacksonException exception) {
      throw new ProtocolException("Malformed JSON");
    }
  }
}
