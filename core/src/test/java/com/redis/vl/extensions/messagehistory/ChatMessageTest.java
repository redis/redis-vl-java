package com.redis.vl.extensions.messagehistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.utils.Utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for ChatMessage */
@DisplayName("ChatMessage Tests")
class ChatMessageTest {

  @Test
  @DisplayName("Should create ChatMessage with all fields")
  void shouldCreateChatMessageWithAllFields() {
    ChatMessage message =
        ChatMessage.builder()
            .role("user")
            .content("Hello, world!")
            .sessionTag("session123")
            .timestamp(1234567890.123)
            .toolCallId("tool456")
            .build();

    assertThat(message.getRole()).isEqualTo("user");
    assertThat(message.getContent()).isEqualTo("Hello, world!");
    assertThat(message.getSessionTag()).isEqualTo("session123");
    assertThat(message.getTimestamp()).isEqualTo(1234567890.123);
    assertThat(message.getToolCallId()).isEqualTo("tool456");
  }

  @Test
  @DisplayName("Should generate entry_id with UUID suffix when not set")
  void shouldGenerateEntryIdWithUuidSuffix() {
    String sessionTag = "session123";
    Double timestamp = Utils.currentTimestamp();

    ChatMessage message =
        ChatMessage.builder()
            .role("user")
            .content("Test message")
            .sessionTag(sessionTag)
            .timestamp(timestamp)
            .build();

    Map<String, Object> dict = message.toDict();
    String entryId = (String) dict.get("entry_id");

    // Verify ID format is session:timestamp:uuid
    assertThat(entryId).startsWith(sessionTag + ":" + timestamp + ":");

    // Verify the UUID suffix is 8 hex characters
    String[] parts = entryId.split(":");
    assertThat(parts).hasSize(3);
    assertThat(parts[2]).hasSize(8);
    assertThat(parts[2]).matches("[0-9a-f]{8}");
  }

  @Test
  @DisplayName("Should generate unique IDs for rapidly created messages with same timestamp")
  void shouldGenerateUniqueIdsForRapidlyCreatedMessages() {
    String sessionTag = UUID.randomUUID().toString();
    Double timestamp = Utils.currentTimestamp();

    // Create multiple messages with the same session and timestamp
    List<ChatMessage> messages = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ChatMessage msg =
          ChatMessage.builder()
              .role("user")
              .content("Message " + i)
              .sessionTag(sessionTag)
              .timestamp(timestamp)
              .build();
      messages.add(msg);
    }

    // All IDs should be unique
    Set<String> ids = new HashSet<>();
    for (ChatMessage msg : messages) {
      Map<String, Object> dict = msg.toDict();
      String entryId = (String) dict.get("entry_id");
      ids.add(entryId);
    }

    assertThat(ids).as("All message IDs should be unique").hasSize(messages.size());

    // All IDs should start with the same session:timestamp prefix
    String expectedPrefix = sessionTag + ":" + timestamp + ":";
    for (ChatMessage msg : messages) {
      Map<String, Object> dict = msg.toDict();
      String entryId = (String) dict.get("entry_id");
      assertThat(entryId).startsWith(expectedPrefix);
    }
  }

  @Test
  @DisplayName("Should preserve explicit entry_id when set")
  void shouldPreserveExplicitEntryId() {
    String explicitId = "my-custom-id-123";

    ChatMessage message =
        ChatMessage.builder()
            .role("user")
            .content("Test message")
            .sessionTag("session123")
            .entryId(explicitId)
            .build();

    Map<String, Object> dict = message.toDict();
    String entryId = (String) dict.get("entry_id");

    // Explicit ID should be preserved
    assertThat(entryId).isEqualTo(explicitId);
  }

  @Test
  @DisplayName("Should generate timestamp when not set")
  void shouldGenerateTimestampWhenNotSet() {
    ChatMessage message =
        ChatMessage.builder().role("user").content("Test message").sessionTag("session123").build();

    Map<String, Object> dict = message.toDict();
    Double timestamp = (Double) dict.get("timestamp");

    assertThat(timestamp).isNotNull();
    assertThat(timestamp).isPositive();
  }

  @Test
  @DisplayName("Should convert to and from dict correctly")
  void shouldConvertToAndFromDict() {
    ChatMessage original =
        ChatMessage.builder()
            .role("assistant")
            .content("Hello!")
            .sessionTag("session456")
            .timestamp(1234567890.0)
            .entryId("session456:1234567890.0:abc12345")
            .toolCallId("tool789")
            .build();

    Map<String, Object> dict = original.toDict();
    ChatMessage restored = ChatMessage.fromDict(dict);

    assertThat(restored.getRole()).isEqualTo(original.getRole());
    assertThat(restored.getContent()).isEqualTo(original.getContent());
    assertThat(restored.getSessionTag()).isEqualTo(original.getSessionTag());
    assertThat(restored.getTimestamp()).isEqualTo(original.getTimestamp());
    assertThat(restored.getEntryId()).isEqualTo(original.getEntryId());
    assertThat(restored.getToolCallId()).isEqualTo(original.getToolCallId());
  }

  @Test
  @DisplayName("Should handle message without tool_call_id")
  void shouldHandleMessageWithoutToolCallId() {
    ChatMessage message =
        ChatMessage.builder().role("user").content("Test message").sessionTag("session123").build();

    Map<String, Object> dict = message.toDict();

    // tool_call_id should not be in dict when null
    assertThat(dict).doesNotContainKey("tool_call_id");
  }
}
