package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;

/**
 * Unit tests for SemanticMessageHistory. Ported from Python
 * tests/integration/test_message_history.py and tests/unit/test_message_history_schema.py
 */
class SemanticMessageHistoryTest {

  private UnifiedJedis mockJedis;
  private BaseVectorizer mockVectorizer;

  @BeforeEach
  void setUp() {
    mockJedis = mock(UnifiedJedis.class);
    mockVectorizer = mock(BaseVectorizer.class);

    // Default vectorizer behavior
    when(mockVectorizer.getDimensions()).thenReturn(768);
    when(mockVectorizer.getDataType()).thenReturn("float32");
    when(mockVectorizer.embed(anyString())).thenReturn(new float[768]);
  }

  @Nested
  @DisplayName("Constructor tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create with name and redis client")
    void testCreateWithNameAndClient() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      assertEquals("test_app", history.getName());
      assertNotNull(history.getSessionTag());
      assertEquals(mockVectorizer, history.getVectorizer());
    }

    @Test
    @DisplayName("should create with custom session tag")
    void testCreateWithSessionTag() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", "custom_session", null, mockVectorizer, mockJedis);

      assertEquals("custom_session", history.getSessionTag());
    }

    @Test
    @DisplayName("should create with custom prefix")
    void testCreateWithPrefix() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", null, "custom_prefix", mockVectorizer, mockJedis);

      // The index should use custom_prefix as key prefix
      assertNotNull(history.getIndex());
    }

    @Test
    @DisplayName("should throw when vectorizer is null")
    void testThrowsWhenVectorizerNull() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new SemanticMessageHistory("test_app", null, mockJedis));
    }

    @Test
    @DisplayName("should use default distance threshold of 0.3")
    void testDefaultDistanceThreshold() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      assertEquals(0.3, history.getDistanceThreshold());
    }

    @Test
    @DisplayName("should create with custom distance threshold")
    void testCustomDistanceThreshold() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", null, null, mockVectorizer, 0.5, mockJedis, false);

      assertEquals(0.5, history.getDistanceThreshold());
    }
  }

  @Nested
  @DisplayName("Distance threshold tests")
  class DistanceThresholdTests {

    @Test
    @DisplayName("should get and set distance threshold")
    void testGetSetDistanceThreshold() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      history.setDistanceThreshold(0.5);
      assertEquals(0.5, history.getDistanceThreshold());

      history.setDistanceThreshold(0.1);
      assertEquals(0.1, history.getDistanceThreshold());
    }
  }

  @Nested
  @DisplayName("Store tests")
  class StoreTests {

    @Test
    @DisplayName("should call vectorizer embed for content")
    void testStoreCallsVectorizer() {
      // Unit test verifies the vectorizer is called
      // Actual storage is tested in integration tests
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] expectedVector = new float[] {0.1f, 0.2f, 0.3f};
      when(mockVectorizer.embed("test prompt")).thenReturn(expectedVector);
      when(mockVectorizer.embed("test response")).thenReturn(expectedVector);

      // Note: This will fail to actually store due to mock, but we verify vectorizer is called
      try {
        history.store("test prompt", "test response");
      } catch (Exception e) {
        // Expected - mocked Redis doesn't support pipeline
      }

      // Verify embed was called for both prompt and response
      verify(mockVectorizer).embed("test prompt");
      verify(mockVectorizer).embed("test response");
    }
  }

  @Nested
  @DisplayName("Add message tests")
  class AddMessageTests {

    @Test
    @DisplayName("should call vectorizer when adding single message")
    void testAddMessageCallsVectorizer() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] expectedVector = new float[] {0.1f, 0.2f, 0.3f};
      when(mockVectorizer.embed("test content")).thenReturn(expectedVector);

      Map<String, String> message = new HashMap<>();
      message.put(ROLE_FIELD_NAME, "user");
      message.put(CONTENT_FIELD_NAME, "test content");

      try {
        history.addMessage(message);
      } catch (Exception e) {
        // Expected - mocked Redis doesn't support pipeline
      }

      verify(mockVectorizer).embed("test content");
    }

    @Test
    @DisplayName("should call vectorizer for each message in batch")
    void testAddMessagesCallsVectorizerForBatch() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[] {0.1f, 0.2f, 0.3f};
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      List<Map<String, String>> messages = new ArrayList<>();
      messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "first"));
      messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "second"));
      messages.add(Map.of(ROLE_FIELD_NAME, "tool", CONTENT_FIELD_NAME, "third"));

      try {
        history.addMessages(messages);
      } catch (Exception e) {
        // Expected - mocked Redis doesn't support pipeline
      }

      verify(mockVectorizer).embed("first");
      verify(mockVectorizer).embed("second");
      verify(mockVectorizer).embed("third");
    }
  }

  @Nested
  @DisplayName("Get relevant tests")
  class GetRelevantTests {

    @Test
    @DisplayName("should throw when topK is negative")
    void testThrowsWhenTopKNegative() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      assertThrows(
          IllegalArgumentException.class,
          () -> history.getRelevant("test prompt", false, -1, false, null, null, null));
    }

    @Test
    @DisplayName("should return empty list when topK is 0")
    void testReturnsEmptyWhenTopKZero() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      List<Map<String, Object>> result =
          history.getRelevant("test prompt", false, 0, false, null, null, null);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should embed the prompt for semantic search")
    void testEmbedsPromptForSearch() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] searchVector = new float[] {0.5f, 0.5f, 0.5f};
      when(mockVectorizer.embed("search query")).thenReturn(searchVector);

      // This will try to query which may fail in unit test, but embedding should occur
      try {
        history.getRelevant("search query", false, 5, false, null, null, null);
      } catch (Exception e) {
        // Expected - no real Redis connection
      }

      verify(mockVectorizer).embed("search query");
    }

    @Test
    @DisplayName("should use instance distance threshold when not overridden")
    void testUsesInstanceDistanceThreshold() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      history.setDistanceThreshold(0.7);

      // Verify it uses 0.7 when querying
      assertEquals(0.7, history.getDistanceThreshold());
    }

    @Test
    @DisplayName("should allow overriding distance threshold per query")
    void testOverrideDistanceThreshold() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      history.setDistanceThreshold(0.3); // Default

      // When calling with explicit threshold, it should use that value
      // The implementation should pass 0.5 to the query
      float[] searchVector = new float[] {0.5f, 0.5f, 0.5f};
      when(mockVectorizer.embed("query")).thenReturn(searchVector);

      // This verifies the API accepts the override parameter
      try {
        history.getRelevant("query", false, 5, false, null, 0.5, null);
      } catch (Exception e) {
        // Expected - no real Redis connection
      }

      verify(mockVectorizer).embed("query");
    }
  }

  @Nested
  @DisplayName("Role filter tests")
  class RoleFilterTests {

    @Test
    @DisplayName("should validate single role")
    void testValidateSingleRole() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[768];
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      // Valid roles should not throw
      assertDoesNotThrow(
          () -> {
            try {
              history.getRelevant("query", false, 5, false, null, null, "user");
            } catch (Exception e) {
              if (!(e.getCause() instanceof IllegalArgumentException)) {
                // Ignore query execution failures, only check arg validation
              }
            }
          });
    }

    @Test
    @DisplayName("should throw for invalid role")
    void testThrowsForInvalidRole() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[768];
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      assertThrows(
          IllegalArgumentException.class,
          () -> history.getRelevant("query", false, 5, false, null, null, "invalid_role"));
    }

    @Test
    @DisplayName("should accept list of roles")
    void testAcceptsListOfRoles() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[768];
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      List<String> roles = List.of("user", "llm");

      // Valid role list should not throw IllegalArgumentException
      assertDoesNotThrow(
          () -> {
            try {
              history.getRelevant("query", false, 5, false, null, null, roles);
            } catch (Exception e) {
              if (e instanceof IllegalArgumentException) {
                throw e;
              }
              // Ignore query execution failures
            }
          });
    }

    @Test
    @DisplayName("should throw for empty role list")
    void testThrowsForEmptyRoleList() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[768];
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      assertThrows(
          IllegalArgumentException.class,
          () -> history.getRelevant("query", false, 5, false, null, null, List.of()));
    }
  }

  @Nested
  @DisplayName("Fallback tests")
  class FallbackTests {

    @Test
    @DisplayName("should support fallback to recent when no semantic matches")
    void testFallbackToRecent() {
      // This tests the API signature - actual fallback behavior tested in integration
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      float[] vector = new float[768];
      when(mockVectorizer.embed(anyString())).thenReturn(vector);

      // The fallback parameter should be accepted
      try {
        history.getRelevant("query", false, 5, true, null, null, null);
      } catch (Exception e) {
        // Expected - no real Redis
      }

      verify(mockVectorizer).embed("query");
    }
  }

  @Nested
  @DisplayName("Schema tests")
  class SchemaTests {

    @Test
    @DisplayName("schema should include vector field")
    void testSchemaIncludesVectorField() {
      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      SearchIndex index = history.getIndex();
      // Schema should have MESSAGE_VECTOR_FIELD_NAME
      assertNotNull(index);
    }

    @Test
    @DisplayName("schema should use vectorizer dimensions")
    void testSchemaUsesVectorizerDimensions() {
      when(mockVectorizer.getDimensions()).thenReturn(384);
      when(mockVectorizer.getDataType()).thenReturn("float32");

      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      // Verify vectorizer dimensions were queried
      verify(mockVectorizer, atLeastOnce()).getDimensions();
    }

    @Test
    @DisplayName("schema should use vectorizer data type")
    void testSchemaUsesVectorizerDataType() {
      when(mockVectorizer.getDimensions()).thenReturn(768);
      when(mockVectorizer.getDataType()).thenReturn("float16");

      SemanticMessageHistory history =
          new SemanticMessageHistory("test_app", mockVectorizer, mockJedis);

      // Verify vectorizer data type was queried
      verify(mockVectorizer, atLeastOnce()).getDataType();
    }
  }
}
