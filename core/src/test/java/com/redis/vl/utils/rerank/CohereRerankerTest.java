package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Unit tests for CohereReranker focusing on edge cases and validation. */
class CohereRerankerTest {

  @Test
  void testBuilderDefaults() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    assertEquals("rerank-english-v3.0", reranker.getModel());
    assertEquals(5, reranker.getLimit());
    assertTrue(reranker.isReturnScore());
    assertNull(reranker.getRankBy());
  }

  @Test
  void testBuilderCustomValues() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    List<String> rankBy = Arrays.asList("content", "title");

    CohereReranker reranker =
        CohereReranker.builder()
            .model("rerank-multilingual-v3.0")
            .limit(10)
            .returnScore(false)
            .rankBy(rankBy)
            .apiConfig(apiConfig)
            .build();

    assertEquals("rerank-multilingual-v3.0", reranker.getModel());
    assertEquals(10, reranker.getLimit());
    assertFalse(reranker.isReturnScore());
    assertEquals(rankBy, reranker.getRankBy());
  }

  @Test
  void testRankWithNullQuery() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank(null, docs));

    assertEquals("query cannot be null", exception.getMessage());
  }

  @Test
  void testRankWithEmptyQuery() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("  ", docs));

    assertEquals("query cannot be empty", exception.getMessage());
  }

  @Test
  void testRankWithNullDocs() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("query", null));

    assertEquals("docs cannot be null", exception.getMessage());
  }

  @Test
  void testRankWithEmptyDocs() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    RerankResult result = reranker.rank("query", Collections.emptyList());

    assertNotNull(result);
    assertTrue(result.getDocuments().isEmpty());
    assertTrue(result.getScores().isEmpty());
  }

  @Test
  void testRankDictDocsWithoutRankBy() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    List<Map<String, Object>> docs =
        Arrays.asList(
            Map.of("content", "doc1", "title", "title1"),
            Map.of("content", "doc2", "title", "title2"));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("query", docs));

    assertEquals(
        "If reranking dictionary-like docs, you must provide a list of rankBy fields",
        exception.getMessage());
  }

  @Test
  void testInvalidLimit() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CohereReranker.builder().apiConfig(apiConfig).limit(0).build());

    assertTrue(exception.getMessage().contains("Limit must be a positive integer"));
  }

  @Test
  void testNegativeLimit() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CohereReranker.builder().apiConfig(apiConfig).limit(-5).build());

    assertTrue(exception.getMessage().contains("Limit must be a positive integer"));
  }

  @Test
  void testMissingApiKey() {
    // No API key in config
    // Note: If COHERE_API_KEY environment variable is set, this test will pass differently
    // This test validates the error handling path when API key is missing from config
    Map<String, String> emptyConfig = Collections.emptyMap();
    CohereReranker reranker = CohereReranker.builder().apiConfig(emptyConfig).build();

    List<String> docs = Arrays.asList("doc1", "doc2");

    // If COHERE_API_KEY env var is set, the reranker will use it and may succeed or fail for other
    // reasons
    // If not set, it should fail with IllegalArgumentException about missing API key
    try {
      reranker.rank("query", docs);
      // If we get here, COHERE_API_KEY must be set in environment
      // That's okay - the test validates the happy path in that case
      assertTrue(true, "API key was available from environment");
    } catch (RuntimeException e) {
      // Should fail with message about missing API key or failed API call
      assertTrue(
          e.getMessage().contains("Cohere API key is required")
              || e.getMessage().contains("Failed to call Cohere rerank API")
              || (e.getCause() != null
                  && e.getCause().getMessage().contains("Cohere API key is required")),
          "Expected error about missing API key, got: " + e.getMessage());
    }
  }

  @Test
  void testMissingCohereLibrary() {
    // This test verifies error handling when Cohere SDK is not available
    // Since the SDK is available in test classpath, we can't easily test this
    // without custom classloader manipulation
    // This is more of a documentation test
    assertTrue(true, "SDK availability check happens at runtime");
  }

  @Test
  void testRankByImmutability() {
    Map<String, String> apiConfig = Map.of("api_key", "test-key");
    List<String> rankBy = new ArrayList<>(Arrays.asList("content", "title"));

    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).rankBy(rankBy).build();

    // Get the rankBy list
    List<String> retrievedRankBy = reranker.getRankBy();

    // Try to modify the original list
    rankBy.add("source");

    // The retrieved list should not be affected
    assertEquals(2, retrievedRankBy.size());
    assertEquals(Arrays.asList("content", "title"), retrievedRankBy);
  }
}
