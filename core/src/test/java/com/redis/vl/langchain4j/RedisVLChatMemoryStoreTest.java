package com.redis.vl.langchain4j;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

/**
 * Test for RedisVLChatMemoryStore - LangChain4J integration.
 *
 * <p>Tests the ChatMemoryStore implementation using Redis for conversation persistence.
 */
@Tag("integration")
class RedisVLChatMemoryStoreTest {

  private JedisPooled jedis;
  private RedisVLChatMemoryStore chatMemoryStore;
  private static final String SESSION_ID = "test-session-123";

  @BeforeEach
  void setUp() {
    jedis = new JedisPooled("localhost", 6379);
    chatMemoryStore = new RedisVLChatMemoryStore(jedis, "test_chat:");
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    if (chatMemoryStore != null && jedis != null) {
      chatMemoryStore.deleteMessages(SESSION_ID);
    }
    if (jedis != null) {
      jedis.close();
    }
  }

  @Test
  void testStoreAndRetrieveMessages() {
    // Given
    List<ChatMessage> messages =
        List.of(
            UserMessage.from("Hello!"),
            AiMessage.from("Hi! How can I help you?"),
            UserMessage.from("What is Redis?"),
            AiMessage.from("Redis is an in-memory database."));

    // When
    chatMemoryStore.updateMessages(SESSION_ID, messages);
    List<ChatMessage> retrieved = chatMemoryStore.getMessages(SESSION_ID);

    // Then
    assertEquals(4, retrieved.size());
    assertEquals("Hello!", ((UserMessage) retrieved.get(0)).singleText());
    assertEquals("Hi! How can I help you?", ((AiMessage) retrieved.get(1)).text());
  }

  @Test
  void testUpdateMessages() {
    // Given - Store initial messages
    chatMemoryStore.updateMessages(
        SESSION_ID, List.of(UserMessage.from("First"), AiMessage.from("Response")));

    // When - Update with more messages
    chatMemoryStore.updateMessages(
        SESSION_ID,
        List.of(
            UserMessage.from("First"),
            AiMessage.from("Response"),
            UserMessage.from("Second"),
            AiMessage.from("Another response")));

    List<ChatMessage> retrieved = chatMemoryStore.getMessages(SESSION_ID);

    // Then
    assertEquals(4, retrieved.size());
  }

  @Test
  void testDeleteMessages() {
    // Given
    chatMemoryStore.updateMessages(SESSION_ID, List.of(UserMessage.from("Test")));

    // When
    chatMemoryStore.deleteMessages(SESSION_ID);
    List<ChatMessage> retrieved = chatMemoryStore.getMessages(SESSION_ID);

    // Then
    assertTrue(retrieved.isEmpty());
  }

  @Test
  void testGetMessagesFromEmptySession() {
    // When
    List<ChatMessage> messages = chatMemoryStore.getMessages("non-existent-session");

    // Then
    assertTrue(messages.isEmpty());
  }

  @Test
  void testMultipleSessions() {
    // Given
    String session1 = "session-1";
    String session2 = "session-2";

    chatMemoryStore.updateMessages(session1, List.of(UserMessage.from("Session 1")));
    chatMemoryStore.updateMessages(session2, List.of(UserMessage.from("Session 2")));

    // When
    List<ChatMessage> messages1 = chatMemoryStore.getMessages(session1);
    List<ChatMessage> messages2 = chatMemoryStore.getMessages(session2);

    // Then
    assertEquals(1, messages1.size());
    assertEquals(1, messages2.size());
    assertEquals("Session 1", ((UserMessage) messages1.get(0)).singleText());
    assertEquals("Session 2", ((UserMessage) messages2.get(0)).singleText());

    // Cleanup
    chatMemoryStore.deleteMessages(session1);
    chatMemoryStore.deleteMessages(session2);
  }
}
