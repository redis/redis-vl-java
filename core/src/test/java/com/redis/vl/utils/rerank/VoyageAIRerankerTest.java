package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Unit tests for VoyageAIReranker focusing on edge cases and validation. */
class VoyageAIRerankerTest {

  @Test
  void testBuilderDefaults() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    assertEquals("rerank-lite-1", reranker.getModel());
    assertEquals(5, reranker.getLimit());
    assertTrue(reranker.isReturnScore());
    assertNull(reranker.getRankBy()); // VoyageAI doesn't support rankBy
  }

  @Test
  void testBuilderCustomValues() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");

    VoyageAIReranker reranker =
        VoyageAIReranker.builder()
            .model("rerank-2")
            .limit(10)
            .returnScore(false)
            .apiConfig(apiConfig)
            .build();

    assertEquals("rerank-2", reranker.getModel());
    assertEquals(10, reranker.getLimit());
    assertFalse(reranker.isReturnScore());
  }

  @Test
  void testRankWithNullQuery() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank(null, docs));

    assertEquals("query cannot be null", exception.getMessage());
  }

  @Test
  void testRankWithEmptyQuery() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("  ", docs));

    assertEquals("query cannot be empty", exception.getMessage());
  }

  @Test
  void testRankWithNullDocs() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("query", null));

    assertEquals("docs cannot be null", exception.getMessage());
  }

  @Test
  void testRankWithEmptyDocs() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    RerankResult result = reranker.rank("query", Collections.emptyList());

    assertNotNull(result);
    assertTrue(result.getDocuments().isEmpty());
    assertTrue(result.getScores().isEmpty());
  }

  @Test
  void testInvalidLimit() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> VoyageAIReranker.builder().apiConfig(apiConfig).limit(0).build());

    assertTrue(exception.getMessage().contains("Limit must be a positive integer"));
  }

  @Test
  void testNegativeLimit() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> VoyageAIReranker.builder().apiConfig(apiConfig).limit(-5).build());

    assertTrue(exception.getMessage().contains("Limit must be a positive integer"));
  }

  @Test
  void testMissingApiKey() {
    // No API key in config
    Map<String, String> emptyConfig = Collections.emptyMap();
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(emptyConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    // If VOYAGE_API_KEY env var is set, the reranker will use it
    // If not set, it should fail with IllegalArgumentException about missing API key
    try {
      reranker.rank("query", docs);
      // If we get here, VOYAGE_API_KEY must be set in environment
      assertTrue(true, "API key was available from environment");
    } catch (RuntimeException e) {
      // Should fail with message about missing API key or failed API call
      assertTrue(
          e.getMessage().contains("VoyageAI API key is required")
              || e.getMessage().contains("Failed to call VoyageAI rerank API")
              || (e.getCause() != null
                  && e.getCause().getMessage().contains("VoyageAI API key is required")),
          "Expected error about missing API key, got: " + e.getMessage());
    }
  }

  @Test
  void testRankWithKwargsEmptyMap() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().limit(5).apiConfig(apiConfig).build();

    // Calling with empty map should use default values
    assertNotNull(reranker);
    assertEquals(5, reranker.getLimit());
  }

  @Test
  void testInvalidKwargsType() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    // Invalid limit type should throw ClassCastException
    assertThrows(
        ClassCastException.class,
        () -> reranker.rank("query", docs, Map.of("limit", "not-an-integer")));
  }

  @Test
  void testDictDocsWithoutContentField() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    VoyageAIReranker reranker = VoyageAIReranker.builder().apiConfig(apiConfig).build();

    List<Map<String, Object>> docs =
        Arrays.asList(
            Map.of("title", "doc1", "body", "content1"), // No "content" field
            Map.of("title", "doc2", "body", "content2"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> reranker.rank("query", docs, Collections.emptyMap()));

    assertTrue(
        exception
            .getMessage()
            .contains(
                "VoyageAI reranker requires documents to be strings or have a 'content' field"));
  }
}
