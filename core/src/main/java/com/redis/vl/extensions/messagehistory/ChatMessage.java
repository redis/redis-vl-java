package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.utils.Utils;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single chat message exchanged between a user and an LLM.
 *
 * <p>Matches the Python ChatMessage model from redisvl.extensions.message_history.schema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

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

  /**
   * Converts the ChatMessage to a Map suitable for storing in Redis.
   *
   * @return Map representation of the message
   */
  public Map<String, Object> toDict() {
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

    return data;
  }

  /**
   * Creates a ChatMessage from a Map (typically from Redis).
   *
   * @param data Map containing message fields
   * @return ChatMessage instance
   */
  public static ChatMessage fromDict(Map<String, Object> data) {
    return ChatMessage.builder()
        .entryId((String) data.get(ID_FIELD_NAME))
        .role((String) data.get(ROLE_FIELD_NAME))
        .content((String) data.get(CONTENT_FIELD_NAME))
        .sessionTag((String) data.get(SESSION_FIELD_NAME))
        .timestamp(convertToDouble(data.get(TIMESTAMP_FIELD_NAME)))
        .toolCallId((String) data.get(TOOL_FIELD_NAME))
        .build();
  }

  private static Double convertToDouble(Object value) {
    if (value == null) return null;
    if (value instanceof Double) return (Double) value;
    if (value instanceof Number) return ((Number) value).doubleValue();
    if (value instanceof String) return Double.parseDouble((String) value);
    return null;
  }
}
