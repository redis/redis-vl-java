package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RerankResult.
 *
 * <p>Following TDD approach - these tests define expected behavior before implementation.
 */
class RerankResultTest {

  @Test
  void testCreateResultWithScores() {
    List<String> docs = Arrays.asList("doc1", "doc2", "doc3");
    List<Double> scores = Arrays.asList(0.9, 0.7, 0.5);

    RerankResult result = new RerankResult(docs, scores);

    assertEquals(docs, result.getDocuments());
    assertEquals(scores, result.getScores());
    assertTrue(result.hasScores());
  }

  @Test
  void testCreateResultWithoutScores() {
    List<String> docs = Arrays.asList("doc1", "doc2", "doc3");

    RerankResult result = new RerankResult(docs, null);

    assertEquals(docs, result.getDocuments());
    assertNull(result.getScores());
    assertFalse(result.hasScores());
  }

  @Test
  void testCreateEmptyResult() {
    List<String> docs = Collections.emptyList();
    List<Double> scores = Collections.emptyList();

    RerankResult result = new RerankResult(docs, scores);

    assertTrue(result.getDocuments().isEmpty());
    assertTrue(result.getScores().isEmpty());
  }

  @Test
  void testResultImmutability() {
    List<String> docs = new ArrayList<>(Arrays.asList("doc1", "doc2"));
    List<Double> scores = new ArrayList<>(Arrays.asList(0.9, 0.7));

    RerankResult result = new RerankResult(docs, scores);

    // Modify original lists
    docs.add("doc3");
    scores.add(0.5);

    // Result should not be affected
    assertEquals(2, result.getDocuments().size());
    assertEquals(2, result.getScores().size());
  }

  @Test
  void testMismatchedDocumentsAndScores() {
    List<String> docs = Arrays.asList("doc1", "doc2");
    List<Double> scores = Arrays.asList(0.9, 0.7, 0.5); // More scores than docs

    // Should throw exception for mismatched sizes
    assertThrows(IllegalArgumentException.class, () -> new RerankResult(docs, scores));
  }

  @Test
  void testNullDocuments() {
    List<Double> scores = Arrays.asList(0.9, 0.7);

    assertThrows(IllegalArgumentException.class, () -> new RerankResult(null, scores));
  }

  @Test
  void testResultWithMaps() {
    List<Map<String, Object>> docs = new ArrayList<>();
    docs.add(Map.of("content", "doc1", "id", "1"));
    docs.add(Map.of("content", "doc2", "id", "2"));

    List<Double> scores = Arrays.asList(0.9, 0.7);

    RerankResult result = new RerankResult(docs, scores);

    assertEquals(2, result.getDocuments().size());
    assertEquals(2, result.getScores().size());

    @SuppressWarnings("unchecked")
    Map<String, Object> firstDoc = (Map<String, Object>) result.getDocuments().get(0);
    assertEquals("doc1", firstDoc.get("content"));
  }

  @Test
  void testGetTopScore() {
    List<String> docs = Arrays.asList("doc1", "doc2", "doc3");
    List<Double> scores = Arrays.asList(0.9, 0.7, 0.5);

    RerankResult result = new RerankResult(docs, scores);

    assertEquals(0.9, result.getTopScore());
  }

  @Test
  void testGetTopScoreWithNoScores() {
    List<String> docs = Arrays.asList("doc1", "doc2");

    RerankResult result = new RerankResult(docs, null);

    assertThrows(IllegalStateException.class, result::getTopScore);
  }

  @Test
  void testGetTopScoreWithEmptyScores() {
    List<String> docs = Collections.emptyList();
    List<Double> scores = Collections.emptyList();

    RerankResult result = new RerankResult(docs, scores);

    assertThrows(IllegalStateException.class, result::getTopScore);
  }
}
