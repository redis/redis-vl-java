package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for HFCrossEncoderReranker with real HuggingFace models.
 *
 * <p>These tests download and run actual ONNX cross-encoder models. Tagged as "integration" to run
 * separately from fast unit tests.
 *
 * <p>The default model (cross-encoder/ms-marco-MiniLM-L-6-v2) is ~80MB and will be cached after
 * first download.
 */
@Tag("integration")
class HFCrossEncoderRerankerIntegrationTest {

  private static HFCrossEncoderReranker reranker;

  @BeforeAll
  static void setUp() {
    // This will download the model on first run (~80MB)
    reranker = HFCrossEncoderReranker.builder().limit(3).returnScore(true).build();
  }

  @Test
  void testRankWithStringDocuments() {
    List<String> docs = Arrays.asList("document one", "document two", "document three");
    String query = "search query";

    RerankResult result = reranker.rank(query, docs);

    assertNotNull(result);
    assertNotNull(result.getDocuments());
    assertNotNull(result.getScores());
    assertEquals(3, result.getDocuments().size());
    assertEquals(3, result.getScores().size());

    // All scores should be valid floats
    for (Double score : result.getScores()) {
      assertNotNull(score);
      assertTrue(Double.isFinite(score));
    }
  }

  @Test
  void testRankWithMapDocuments() {
    List<Map<String, Object>> docs = new ArrayList<>();
    docs.add(Map.of("content", "document one", "id", "1"));
    docs.add(Map.of("content", "document two", "id", "2"));
    docs.add(Map.of("content", "document three", "id", "3"));

    String query = "search query";

    RerankResult result = reranker.rank(query, docs);

    assertNotNull(result);
    assertEquals(3, result.getDocuments().size());
    assertEquals(3, result.getScores().size());

    // Verify documents have content field
    for (Object doc : result.getDocuments()) {
      assertTrue(doc instanceof Map);
      @SuppressWarnings("unchecked")
      Map<String, Object> docMap = (Map<String, Object>) doc;
      assertTrue(docMap.containsKey("content"));
    }
  }

  @Test
  void testSemanticSimilarityScoring() {
    // Test that the model actually understands semantics
    String query = "I love you";
    List<String> texts =
        Arrays.asList("I love you", "I like you", "I don't like you", "I hate you");

    RerankResult result = reranker.rank(query, texts);

    // The exact match should score highest
    assertEquals("I love you", result.getDocuments().get(0));

    // Scores should be descending
    List<Double> scores = result.getScores();
    for (int i = 0; i < scores.size() - 1; i++) {
      assertTrue(
          scores.get(i) >= scores.get(i + 1),
          String.format("Scores should be descending: %f >= %f", scores.get(i), scores.get(i + 1)));
    }
  }

  @Test
  void testRealWorldRelevance() {
    // Test with realistic query-document matching
    String query = "What is the capital of France?";
    List<String> docs =
        Arrays.asList(
            "Paris is the capital of France",
            "London is the capital of England",
            "Berlin is the capital of Germany",
            "The Eiffel Tower is in Paris");

    HFCrossEncoderReranker limitedReranker =
        HFCrossEncoderReranker.builder().limit(2).returnScore(true).build();

    RerankResult result = limitedReranker.rank(query, docs);

    assertEquals(2, result.getDocuments().size());

    // "Paris is the capital of France" should be top result
    String topDoc = (String) result.getDocuments().get(0);
    assertTrue(
        topDoc.contains("Paris") && topDoc.contains("capital"),
        "Top result should be about Paris as capital, but was: " + topDoc);
  }

  @Test
  void testRankEmptyDocuments() {
    String query = "search query";
    List<String> docs = Collections.emptyList();

    RerankResult result = reranker.rank(query, docs);

    assertNotNull(result);
    assertNotNull(result.getDocuments());
    assertTrue(result.getDocuments().isEmpty());
    assertNotNull(result.getScores());
    assertTrue(result.getScores().isEmpty());
  }

  @Test
  void testRankWithoutScores() {
    HFCrossEncoderReranker noScoreReranker =
        HFCrossEncoderReranker.builder().returnScore(false).build();

    List<String> docs = Arrays.asList("document one", "document two", "document three");
    String query = "search query";

    RerankResult result = noScoreReranker.rank(query, docs);

    assertNotNull(result);
    assertNotNull(result.getDocuments());
    assertNull(result.getScores());
  }

  @Test
  void testRankNullQuery() {
    List<String> docs = Arrays.asList("document one");

    assertThrows(IllegalArgumentException.class, () -> reranker.rank(null, docs));
  }

  @Test
  void testRankEmptyQuery() {
    List<String> docs = Arrays.asList("document one");

    Exception exception =
        assertThrows(IllegalArgumentException.class, () -> reranker.rank("", docs));

    assertTrue(exception.getMessage().contains("query"));
  }

  @Test
  void testRankNullDocuments() {
    String query = "search query";

    assertThrows(IllegalArgumentException.class, () -> reranker.rank(query, null));
  }

  @Test
  void testRankLimitFewerThanDocs() {
    List<String> docs =
        Arrays.asList(
            "doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8", "doc9", "doc10");
    String query = "search";

    RerankResult result = reranker.rank(query, docs);

    // Should only return 'limit' docs (3)
    assertEquals(3, result.getDocuments().size());
    assertEquals(3, result.getScores().size());
  }

  @Test
  void testRankWithMapsMissingContentField() {
    List<Map<String, Object>> docs = new ArrayList<>();
    docs.add(Map.of("text", "document one")); // Wrong field name
    docs.add(Map.of("content", "document two"));
    docs.add(Map.of("body", "document three")); // Wrong field name

    String query = "search query";

    // Should only process docs with "content" field
    RerankResult result = reranker.rank(query, docs);

    assertNotNull(result);
    // Should only rank the one doc with "content" field
    assertEquals(1, result.getDocuments().size());
  }

  @Test
  void testCustomModel() {
    // Test with a different cross-encoder model
    HFCrossEncoderReranker customReranker =
        HFCrossEncoderReranker.builder()
            .model("cross-encoder/stsb-distilroberta-base")
            .limit(4)
            .build();

    String query = "I love you";
    List<String> texts =
        Arrays.asList("I love you", "I like you", "I don't like you", "I hate you");

    RerankResult result = customReranker.rank(query, texts);

    assertEquals(4, result.getDocuments().size());

    // Scores should be descending
    List<Double> scores = result.getScores();
    for (int i = 0; i < scores.size() - 1; i++) {
      assertTrue(scores.get(i) >= scores.get(i + 1));
    }
  }
}
