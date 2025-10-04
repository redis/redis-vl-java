package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test for VoyageAIReranker matching Python notebook scenarios.
 *
 * <p>Based on Python notebook: docs/user_guide/06_rerankers.ipynb
 *
 * <p>Requires VOYAGE_API_KEY environment variable to be set.
 */
@Tag("integration")
class VoyageAIRerankerIntegrationTest {

  private static final String QUERY = "What is the capital of the United States?";

  private static final List<String> STRING_DOCS =
      Arrays.asList(
          "Carson City is the capital city of the American state of Nevada. At the 2010 United States Census, Carson City had a population of 55,274.",
          "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean that are a political division controlled by the United States. Its capital is Saipan.",
          "Charlotte Amalie is the capital and largest city of the United States Virgin Islands. It has about 20,000 people. The city is on the island of Saint Thomas.",
          "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district. The President of the USA and many major national government offices are in the territory. This makes it the political center of the United States of America.",
          "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states. The federal government (including the United States military) also uses capital punishment.");

  private static String apiKey;

  @BeforeAll
  static void setUp() {
    apiKey = System.getenv("VOYAGE_API_KEY");
    assertNotNull(apiKey, "VOYAGE_API_KEY environment variable must be set");
    assertFalse(apiKey.isEmpty(), "VOYAGE_API_KEY must not be empty");
  }

  @Test
  void testRerankStringDocuments() {
    // Python notebook scenario: Simple string documents with rerank-lite-1 model
    // Expected output (from Python):
    // 0.796875 -- Washington, D.C. ...
    // 0.578125 -- Charlotte Amalie ...
    // 0.5625 -- Carson City ...

    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    VoyageAIReranker reranker =
        VoyageAIReranker.builder().model("rerank-lite-1").limit(3).apiConfig(apiConfig).build();

    RerankResult result = reranker.rank(QUERY, STRING_DOCS);

    assertNotNull(result);
    List<?> docs = result.getDocuments();
    List<Double> scores = result.getScores();

    // Should return exactly 3 results (limit=3)
    assertEquals(3, docs.size(), "Should return 3 results");
    assertEquals(3, scores.size(), "Should return 3 scores");

    System.out.println("\n=== JAVA STRING DOCS OUTPUT ===\"");
    for (int i = 0; i < docs.size(); i++) {
      String docPreview =
          docs.get(i).toString().substring(0, Math.min(50, docs.get(i).toString().length()));
      System.out.println(scores.get(i) + " -- " + docPreview + "...");
    }

    System.out.println("\n=== EXPECTED PYTHON OUTPUT ===");
    System.out.println("0.796875 -- Washington, D.C. ...");
    System.out.println("0.578125 -- Charlotte Amalie ...");
    System.out.println("0.5625 -- Carson City ...");

    // Top result must be Washington D.C.
    String topDoc = (String) docs.get(0);
    assertTrue(
        topDoc.contains("Washington, D.C."),
        "Top result must be Washington D.C., but was: " + topDoc);

    // Top score should be ~0.797
    double topScore = scores.get(0);
    System.out.println("\n=== SCORE COMPARISON ===");
    System.out.println("Expected top score: ~0.797");
    System.out.println("Actual top score: " + topScore);

    assertTrue(topScore > 0.5, "Top score should be > 0.5, but was: " + topScore);
    assertTrue(
        Math.abs(topScore - 0.797) < 0.1,
        "Top score should be close to 0.797, but was: " + topScore);
  }

  @Test
  void testRuntimeLimitOverride() {
    // Test that limit can be overridden at rank() time
    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    VoyageAIReranker reranker =
        VoyageAIReranker.builder()
            .model("rerank-lite-1")
            .limit(5) // Default limit
            .apiConfig(apiConfig)
            .build();

    // Override limit to 2 at runtime
    RerankResult result = reranker.rank(QUERY, STRING_DOCS, Map.of("limit", 2));

    assertNotNull(result);
    assertEquals(2, result.getDocuments().size(), "Should return 2 results (overridden limit)");
    assertEquals(2, result.getScores().size(), "Should return 2 scores");
  }

  @Test
  void testRuntimeReturnScoreOverride() {
    // Test that return_score can be overridden at rank() time
    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    VoyageAIReranker reranker =
        VoyageAIReranker.builder()
            .model("rerank-lite-1")
            .returnScore(true) // Default return scores
            .apiConfig(apiConfig)
            .build();

    // Override to not return scores
    RerankResult result = reranker.rank(QUERY, STRING_DOCS, Map.of("return_score", false));

    assertNotNull(result);
    assertNotNull(result.getDocuments());
    assertNull(result.getScores(), "Scores should be null when return_score=false");
  }

  @Test
  void testTruncationParameter() {
    // Test truncation parameter
    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    VoyageAIReranker reranker =
        VoyageAIReranker.builder().model("rerank-lite-1").apiConfig(apiConfig).build();

    // Pass truncation parameter
    RerankResult result = reranker.rank(QUERY, STRING_DOCS, Map.of("truncation", true, "limit", 3));

    assertNotNull(result);
    assertEquals(3, result.getDocuments().size(), "Should return 3 results");
    assertTrue(result.getScores().size() > 0, "Should return scores");
  }
}
