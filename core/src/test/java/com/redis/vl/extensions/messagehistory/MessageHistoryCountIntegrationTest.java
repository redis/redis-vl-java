package com.redis.vl.extensions.messagehistory;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import org.junit.jupiter.api.*;

/**
 * Integration tests for count() method in MessageHistory and SemanticMessageHistory.
 *
 * <p>Ported from Python: tests/integration/test_message_history.py (test_standard_count,
 * test_semantic_count)
 */
@Tag("integration")
@DisplayName("MessageHistory count() Integration Tests")
class MessageHistoryCountIntegrationTest extends BaseIntegrationTest {

  @Nested
  @DisplayName("Standard MessageHistory count")
  class StandardCountTests {

    private MessageHistory history;

    @BeforeEach
    void setUp() {
      history = new MessageHistory("test_standard_count", unifiedJedis);
      history.clear();
    }

    @AfterEach
    void tearDown() {
      if (history != null) {
        history.clear();
        history.delete();
      }
    }

    @Test
    @DisplayName("count returns 0 when empty")
    void testCountReturnsZeroWhenEmpty() {
      assertEquals(0, history.count());
    }

    @Test
    @DisplayName("count returns 2 after storing one prompt/response pair, 0 after clear")
    void testStandardCount() {
      history.store("some prompt", "some response");
      assertEquals(2, history.count());

      history.clear();
      assertEquals(0, history.count());
    }

    @Test
    @DisplayName("count with explicit session tag")
    void testCountWithSessionTag() {
      history.store("prompt 1", "response 1", "session_a");
      history.store("prompt 2", "response 2", "session_b");

      assertEquals(2, history.count("session_a"));
      assertEquals(2, history.count("session_b"));
    }

    @Test
    @DisplayName("count defaults to instance session tag")
    void testCountDefaultsToInstanceSession() {
      MessageHistory sessionHistory =
          new MessageHistory("test_count_session", "my-session", null, unifiedJedis);
      try {
        sessionHistory.store("prompt", "response");
        assertEquals(2, sessionHistory.count());
        assertEquals(2, sessionHistory.count("my-session"));
      } finally {
        sessionHistory.clear();
        sessionHistory.delete();
      }
    }

    @Test
    @DisplayName("count reflects multiple stores")
    void testCountMultipleStores() {
      history.store("first prompt", "first response");
      assertEquals(2, history.count());

      history.store("second prompt", "second response");
      assertEquals(4, history.count());
    }
  }

  @Nested
  @DisplayName("Semantic MessageHistory count")
  class SemanticCountTests {

    private SemanticMessageHistory history;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
      com.redis.vl.utils.vectorize.BaseVectorizer vectorizer =
          new com.redis.vl.utils.vectorize.MockVectorizer("mock-model", 768);
      history =
          new SemanticMessageHistory(
              "test_semantic_count", null, null, vectorizer, 0.3, unifiedJedis, true);
    }

    @AfterEach
    void tearDown() {
      if (history != null) {
        history.clear();
        history.delete();
      }
    }

    @Test
    @DisplayName("count returns 0 when empty")
    void testSemanticCountReturnsZeroWhenEmpty() {
      assertEquals(0, history.count());
    }

    @Test
    @DisplayName("count returns 2 after storing one prompt/response pair, 0 after clear")
    void testSemanticCount() {
      history.store("first prompt", "first response");
      assertEquals(2, history.count());

      history.clear();
      assertEquals(0, history.count());
    }

    @Test
    @DisplayName("count with explicit session tag")
    void testSemanticCountWithSessionTag() {
      history.store("prompt 1", "response 1", "session_x");
      history.store("prompt 2", "response 2", "session_y");

      assertEquals(2, history.count("session_x"));
      assertEquals(2, history.count("session_y"));
    }
  }
}
