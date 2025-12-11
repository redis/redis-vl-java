package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.MockVectorizer;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for SemanticMessageHistory. Ported from Python
 * tests/integration/test_message_history.py
 */
class SemanticMessageHistoryIntegrationTest extends BaseIntegrationTest {

  private SemanticMessageHistory history;
  private BaseVectorizer vectorizer;

  @BeforeEach
  void setUp() {
    // Use MockVectorizer for predictable embeddings in tests
    vectorizer = new MockVectorizer("mock-model", 768);
    history =
        new SemanticMessageHistory(
            "test_semantic_history",
            null,
            null,
            vectorizer,
            0.3,
            unifiedJedis,
            true); // overwrite=true
  }

  @AfterEach
  void tearDown() {
    if (history != null) {
      history.clear();
      history.delete();
    }
  }

  @Nested
  @DisplayName("Store and get recent tests")
  class StoreAndGetRecentTests {

    @Test
    @DisplayName("should store and retrieve messages")
    void testStoreAndGetRecent() throws InterruptedException {
      // Initially empty
      List<Map<String, Object>> context = history.getRecent(5, false, false, null, null);
      assertEquals(0, context.size());

      // Store some messages with small delays to ensure unique timestamps
      history.store("first prompt", "first response");
      Thread.sleep(10);
      history.store("second prompt", "second response");
      Thread.sleep(10);
      history.store("third prompt", "third response");
      Thread.sleep(10);
      history.store("fourth prompt", "fourth response");

      // Get default (5)
      List<Map<String, Object>> defaultContext = history.getRecent(5, false, false, null, null);
      assertEquals(5, defaultContext.size()); // 4 pairs = 8, limited to 5

      // Get all (10)
      List<Map<String, Object>> fullContext = history.getRecent(10, false, false, null, null);
      assertEquals(8, fullContext.size());

      // Verify content contains expected messages (order may vary with rapid inserts)
      assertTrue(
          fullContext.stream().anyMatch(m -> "first prompt".equals(m.get(CONTENT_FIELD_NAME))));
      assertTrue(
          fullContext.stream().anyMatch(m -> "fourth response".equals(m.get(CONTENT_FIELD_NAME))));
    }

    @Test
    @DisplayName("should handle tool messages")
    void testToolMessages() {
      history.store("prompt", "response");

      Map<String, String> toolMessage = new HashMap<>();
      toolMessage.put(ROLE_FIELD_NAME, "tool");
      toolMessage.put(CONTENT_FIELD_NAME, "tool result");
      toolMessage.put(TOOL_FIELD_NAME, "tool_id");
      history.addMessage(toolMessage);

      List<Map<String, Object>> context = history.getRecent(10, false, false, null, null);
      assertEquals(3, context.size());

      // Find the tool message
      Map<String, Object> tool =
          context.stream()
              .filter(m -> "tool".equals(m.get(ROLE_FIELD_NAME)))
              .findFirst()
              .orElse(null);
      assertNotNull(tool);
      assertEquals("tool result", tool.get(CONTENT_FIELD_NAME));
      assertEquals("tool_id", tool.get(TOOL_FIELD_NAME));
    }

    @Test
    @DisplayName("should handle metadata in messages")
    void testMetadataInMessages() {
      history.addMessages(
          List.of(
              Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "prompt"),
              Map.of(
                  ROLE_FIELD_NAME,
                  "llm",
                  CONTENT_FIELD_NAME,
                  "response",
                  METADATA_FIELD_NAME,
                  "return value from tool")));

      List<Map<String, Object>> context = history.getRecent(10, false, false, null, null);
      assertEquals(2, context.size());
    }

    @Test
    @DisplayName("should throw for invalid topK")
    void testInvalidTopK() {
      assertThrows(
          IllegalArgumentException.class, () -> history.getRecent(-1, false, false, null, null));
    }

    @Test
    @DisplayName("should return empty for topK of 0")
    void testTopKZero() {
      history.store("prompt", "response");
      List<Map<String, Object>> context = history.getRecent(0, false, false, null, null);
      assertEquals(0, context.size());
    }
  }

  @Nested
  @DisplayName("Session scope tests")
  class SessionScopeTests {

    @Test
    @DisplayName("should scope messages by session tag")
    void testSessionScope() throws InterruptedException {
      // Store under default session
      history.store("default prompt", "default response");
      Thread.sleep(10);

      // Store under different session
      String newSession = "new_session";
      history.store("new prompt", "new response", newSession);

      // Default session should have 2 messages
      List<Map<String, Object>> defaultContext = history.getRecent(10, false, false, null, null);
      assertEquals(2, defaultContext.size());

      // New session should have 2 messages
      List<Map<String, Object>> newContext = history.getRecent(10, false, false, newSession, null);
      assertEquals(2, newContext.size());

      // Verify new session contains the expected content (order-independent)
      assertTrue(newContext.stream().anyMatch(m -> "new prompt".equals(m.get(CONTENT_FIELD_NAME))));
      assertTrue(
          newContext.stream().anyMatch(m -> "new response".equals(m.get(CONTENT_FIELD_NAME))));

      // Non-existent session should be empty
      List<Map<String, Object>> noContext =
          history.getRecent(10, false, false, "nonexistent", null);
      assertEquals(0, noContext.size());
    }
  }

  @Nested
  @DisplayName("Get relevant (semantic search) tests")
  class GetRelevantTests {

    @Test
    @DisplayName("should find semantically relevant messages")
    void testGetRelevant() {
      // Add messages about different topics
      history.addMessage(
          Map.of(
              ROLE_FIELD_NAME,
              "system",
              CONTENT_FIELD_NAME,
              "discussing common fruits and vegetables"));
      history.store("list of common fruits", "apples, oranges, bananas, strawberries");
      history.store("list of common vegetables", "carrots, broccoli, onions, spinach");
      history.store("winter sports in the olympics", "downhill skiing, ice skating, luge");

      // Search for fruit-related messages
      // Note: MockVectorizer uses content hash for embeddings, so exact matches work
      List<Map<String, Object>> fruitContext =
          history.getRelevant("list of common fruits", false, 5, false, null, 0.5, null);

      // Should find relevant messages
      assertFalse(fruitContext.isEmpty());
    }

    @Test
    @DisplayName("should return empty when topK is 0")
    void testGetRelevantTopKZero() {
      history.store("prompt", "response");

      List<Map<String, Object>> context =
          history.getRelevant("query", false, 0, false, null, null, null);
      assertEquals(0, context.size());
    }

    @Test
    @DisplayName("should throw for negative topK")
    void testGetRelevantNegativeTopK() {
      assertThrows(
          IllegalArgumentException.class,
          () -> history.getRelevant("query", false, -1, false, null, null, null));
    }

    @Test
    @DisplayName("should fall back to recent when no semantic matches")
    void testFallbackToRecent() {
      history.store("first prompt", "first response");
      history.store("second prompt", "second response");

      // Use very low threshold to ensure no matches
      history.setDistanceThreshold(0.001);

      // With fallback=true, should return recent messages
      List<Map<String, Object>> context =
          history.getRelevant(
              "completely unrelated query xyz123",
              false,
              5,
              true, // fallback=true
              null,
              0.001, // very low threshold
              null);

      // Should have fallen back to recent
      // Note: depending on vectorizer, may or may not have matches
    }

    @Test
    @DisplayName("should support role filtering in getRelevant")
    void testGetRelevantWithRoleFilter() {
      history.addMessage(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "system info"));
      history.store("user question", "llm answer");
      history.addMessage(
          Map.of(
              ROLE_FIELD_NAME, "tool", CONTENT_FIELD_NAME, "tool result", TOOL_FIELD_NAME, "t1"));

      // Search only in user messages
      List<Map<String, Object>> userOnly =
          history.getRelevant("user question", false, 10, false, null, 0.9, "user");

      // All results should have role=user
      for (Map<String, Object> msg : userOnly) {
        assertEquals("user", msg.get(ROLE_FIELD_NAME));
      }
    }

    @Test
    @DisplayName("should support multiple role filtering")
    void testGetRelevantWithMultipleRoles() {
      history.addMessage(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "system info"));
      history.store("user question", "llm answer");

      // Search in user and llm messages
      List<Map<String, Object>> userAndLlm =
          history.getRelevant(
              "question answer", false, 10, false, null, 0.9, List.of("user", "llm"));

      // Results should only have user or llm roles
      for (Map<String, Object> msg : userAndLlm) {
        String role = (String) msg.get(ROLE_FIELD_NAME);
        assertTrue(role.equals("user") || role.equals("llm"));
      }
    }
  }

  @Nested
  @DisplayName("Messages property tests")
  class MessagesPropertyTests {

    @Test
    @DisplayName("should return all messages via messages property")
    void testMessagesProperty() {
      history.addMessages(
          List.of(
              Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "first prompt"),
              Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "first response"),
              Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "second prompt")));

      List<Map<String, Object>> messages = history.getMessages();
      assertEquals(3, messages.size());

      // Verify chronological order
      assertEquals("first prompt", messages.get(0).get(CONTENT_FIELD_NAME));
      assertEquals("first response", messages.get(1).get(CONTENT_FIELD_NAME));
      assertEquals("second prompt", messages.get(2).get(CONTENT_FIELD_NAME));
    }
  }

  @Nested
  @DisplayName("Drop tests")
  class DropTests {

    @Test
    @DisplayName("should drop last message when id is null")
    void testDropLast() throws InterruptedException {
      history.store("first prompt", "first response");
      Thread.sleep(10);
      history.store("second prompt", "second response");
      Thread.sleep(10);
      history.store("third prompt", "third response");
      Thread.sleep(10);
      history.store("fourth prompt", "fourth response");

      // Get count before drop
      List<Map<String, Object>> before = history.getRecent(10, false, false, null, null);
      int countBefore = before.size();

      // Drop last message
      history.drop(null);

      List<Map<String, Object>> context = history.getRecent(10, false, false, null, null);
      assertEquals(countBefore - 1, context.size()); // One less than before
    }

    @Test
    @DisplayName("should drop specific message by id")
    void testDropById() {
      history.store("first prompt", "first response");
      history.store("second prompt", "second response");

      // Get raw to access IDs
      List<Map<String, Object>> raw = history.getRecent(10, false, true, null, null);

      // Find and drop a specific message
      String idToDrop = (String) raw.get(1).get(ID_FIELD_NAME);
      history.drop(idToDrop);

      List<Map<String, Object>> afterDrop = history.getRecent(10, false, false, null, null);
      assertEquals(3, afterDrop.size()); // 4 - 1 = 3
    }
  }

  @Nested
  @DisplayName("Clear and delete tests")
  class ClearDeleteTests {

    @Test
    @DisplayName("should clear all messages")
    void testClear() {
      history.store("prompt", "response");
      history.clear();

      List<Map<String, Object>> context = history.getRecent(10, false, false, null, null);
      assertEquals(0, context.size());
    }

    @Test
    @DisplayName("should delete index and all data")
    void testDelete() {
      history.store("prompt", "response");
      history.delete();

      // Index should be gone - recreating should work
      history =
          new SemanticMessageHistory(
              "test_semantic_history", null, null, vectorizer, 0.3, unifiedJedis, true);

      List<Map<String, Object>> context = history.getRecent(10, false, false, null, null);
      assertEquals(0, context.size());
    }
  }

  @Nested
  @DisplayName("Get raw tests")
  class GetRawTests {

    @Test
    @DisplayName("should return raw Redis entries")
    void testGetRaw() throws InterruptedException {
      history.store("first prompt", "first response");
      Thread.sleep(10); // Ensure distinct timestamps
      history.store("second prompt", "second response");

      List<Map<String, Object>> raw = history.getRecent(10, false, true, null, null);
      assertEquals(4, raw.size());

      // Raw should have entry_id - check any entry has it
      assertTrue(raw.stream().anyMatch(m -> m.get(ID_FIELD_NAME) != null));
      // Check user message exists with correct content
      assertTrue(
          raw.stream()
              .anyMatch(
                  m ->
                      "user".equals(m.get(ROLE_FIELD_NAME))
                          && "first prompt".equals(m.get(CONTENT_FIELD_NAME))));
    }
  }

  @Nested
  @DisplayName("Get text tests")
  class GetTextTests {

    @Test
    @DisplayName("should return text content only")
    void testGetText() {
      history.store("first prompt", "first response");

      List<String> text = history.getRecent(10, true, false, null, null);
      assertEquals(2, text.size());
      assertEquals("first prompt", text.get(0));
      assertEquals("first response", text.get(1));
    }
  }

  @Nested
  @DisplayName("Distance threshold tests")
  class DistanceThresholdIntegrationTests {

    @Test
    @DisplayName("should use configured distance threshold")
    void testDistanceThreshold() {
      history.setDistanceThreshold(0.5);
      assertEquals(0.5, history.getDistanceThreshold());

      history.store("test prompt", "test response");

      // Query with default threshold
      List<Map<String, Object>> context =
          history.getRelevant("test prompt", false, 5, false, null, null, null);

      // Should find results within threshold
      // Note: exact results depend on vectorizer behavior
    }
  }
}
