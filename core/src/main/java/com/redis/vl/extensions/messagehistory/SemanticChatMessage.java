package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.utils.ArrayUtils;
import com.redis.vl.utils.Utils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A chat message with vector embedding for semantic search.
 *
 * <p>Extends the ChatMessage model with a vector field for storing embeddings. Matches the Python
 * ChatMessage model from redisvl.extensions.message_history.schema when used with
 * SemanticMessageHistory.
 *
 * <p>This class is final to prevent finalizer attacks (SEI CERT OBJ11-J).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "Vector field is intentionally mutable for builder pattern; defensive copies made in public API")
public final class SemanticChatMessage {

  /** A unique identifier for the message. Generated from session_tag and timestamp. */
  private String entryId;

  /** The role of the message sender (e.g., 'user', 'llm', 'system', 'tool'). */
  private String role;

  /** The content of the message. */
  private String content;

  /** Tag associated with the current conversation session. */
  private String sessionTag;

  /** The time the message was sent, in UTC, rounded to milliseconds. */
  private Double timestamp;

  /** An optional identifier for a tool call associated with the message. */
  private String toolCallId;

  /** The vector representation of the message content. */
  private float[] vectorField;

  /** Optional additional metadata to store alongside the message. */
  private String metadata;

  /**
   * Converts the SemanticChatMessage to a Map suitable for storing in Redis.
   *
   * @param dtype The data type for the vector field (e.g., "float32")
   * @return Map representation of the message
   */
  public Map<String, Object> toDict(String dtype) {
    // Generate timestamp if not set
    if (timestamp == null) {
      timestamp = Utils.currentTimestamp();
    }

    // Generate entry_id if not set
    if (entryId == null) {
      entryId = sessionTag + ":" + timestamp;
    }

    Map<String, Object> data = new HashMap<>();
    data.put(ID_FIELD_NAME, entryId);
    data.put(ROLE_FIELD_NAME, role);
    data.put(CONTENT_FIELD_NAME, content);
    data.put(SESSION_FIELD_NAME, sessionTag);
    data.put(TIMESTAMP_FIELD_NAME, timestamp);

    // Only include tool_call_id if present
    if (toolCallId != null) {
      data.put(TOOL_FIELD_NAME, toolCallId);
    }

    // Only include metadata if present
    if (metadata != null) {
      data.put(METADATA_FIELD_NAME, metadata);
    }

    // Convert vector to byte array for Redis storage
    if (vectorField != null) {
      data.put(MESSAGE_VECTOR_FIELD_NAME, ArrayUtils.floatArrayToBytes(vectorField));
    }

    return data;
  }

  /**
   * Converts the SemanticChatMessage to a Map using default float32 dtype.
   *
   * @return Map representation of the message
   */
  public Map<String, Object> toDict() {
    return toDict("float32");
  }

  /**
   * Creates a SemanticChatMessage from a Map (typically from Redis).
   *
   * @param data Map containing message fields
   * @return SemanticChatMessage instance
   */
  public static SemanticChatMessage fromDict(Map<String, Object> data) {
    SemanticChatMessageBuilder builder =
        SemanticChatMessage.builder()
            .entryId((String) data.get(ID_FIELD_NAME))
            .role((String) data.get(ROLE_FIELD_NAME))
            .content((String) data.get(CONTENT_FIELD_NAME))
            .sessionTag((String) data.get(SESSION_FIELD_NAME))
            .timestamp(convertToDouble(data.get(TIMESTAMP_FIELD_NAME)))
            .toolCallId((String) data.get(TOOL_FIELD_NAME))
            .metadata((String) data.get(METADATA_FIELD_NAME));

    // Handle vector field if present
    Object vectorData = data.get(MESSAGE_VECTOR_FIELD_NAME);
    if (vectorData instanceof byte[]) {
      builder.vectorField(ArrayUtils.bytesToFloatArray((byte[]) vectorData));
    } else if (vectorData instanceof float[]) {
      builder.vectorField((float[]) vectorData);
    }

    return builder.build();
  }

  private static Double convertToDouble(Object value) {
    if (value == null) return null;
    if (value instanceof Double) return (Double) value;
    if (value instanceof Number) return ((Number) value).doubleValue();
    if (value instanceof String) return Double.parseDouble((String) value);
    return null;
  }
}
