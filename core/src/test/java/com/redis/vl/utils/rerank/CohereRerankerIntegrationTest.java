package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test for CohereReranker matching Python notebook scenarios.
 *
 * <p>Based on Python notebook: docs/user_guide/06_rerankers.ipynb
 *
 * <p>Requires COHERE_API_KEY environment variable to be set.
 */
@Tag("integration")
class CohereRerankerIntegrationTest {

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
    apiKey = System.getenv("COHERE_API_KEY");
    assertNotNull(apiKey, "COHERE_API_KEY environment variable must be set");
    assertFalse(apiKey.isEmpty(), "COHERE_API_KEY must not be empty");
  }

  @Test
  void testRerankStringDocuments() {
    // Python notebook scenario 1: Simple string documents
    // Expected output (from Python):
    // 0.9990564 -- Washington, D.C. ...
    // 0.7516481 -- Capital punishment ...
    // 0.08882029 -- Northern Mariana Islands ...

    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    CohereReranker reranker = CohereReranker.builder().limit(3).apiConfig(apiConfig).build();

    RerankResult result = reranker.rank(QUERY, STRING_DOCS);

    assertNotNull(result);
    List<?> docs = result.getDocuments();
    List<Double> scores = result.getScores();

    // Should return exactly 3 results (limit=3)
    assertEquals(3, docs.size(), "Should return 3 results");
    assertEquals(3, scores.size(), "Should return 3 scores");

    System.out.println("\n=== JAVA STRING DOCS OUTPUT ===");
    for (int i = 0; i < docs.size(); i++) {
      String docPreview =
          docs.get(i).toString().substring(0, Math.min(50, docs.get(i).toString().length()));
      System.out.println(scores.get(i) + " -- " + docPreview + "...");
    }

    System.out.println("\n=== EXPECTED PYTHON OUTPUT ===");
    System.out.println("0.9990564 -- Washington, D.C. ...");
    System.out.println("0.7516481 -- Capital punishment ...");
    System.out.println("0.08882029 -- Northern Mariana Islands ...");

    // Top result must be Washington D.C.
    String topDoc = (String) docs.get(0);
    assertTrue(
        topDoc.contains("Washington, D.C."),
        "Top result must be Washington D.C., but was: " + topDoc);

    // Top score should be ~0.999
    double topScore = scores.get(0);
    System.out.println("\n=== SCORE COMPARISON ===");
    System.out.println("Expected top score: ~0.999");
    System.out.println("Actual top score: " + topScore);

    assertTrue(topScore > 0.9, "Top score should be > 0.9, but was: " + topScore);
    assertTrue(
        Math.abs(topScore - 0.999) < 0.05,
        "Top score should be close to 0.999, but was: " + topScore);
  }

  @Test
  void testRerankDictionaryDocumentsWithRankBy() {
    // Python notebook scenario 2: Dictionary documents with rank_by
    // Expected output (from Python):
    // 0.9988121 -- {'source': 'textbook', 'passage': 'Washington, D.C. ...'}
    // 0.5974905 -- {'source': 'wiki', 'passage': 'Capital punishment ...'}
    // 0.059101548 -- {'source': 'encyclopedia', 'passage': 'Northern Mariana ...'}

    List<Map<String, Object>> dictDocs =
        Arrays.asList(
            Map.of(
                "source",
                "wiki",
                "passage",
                "Carson City is the capital city of the American state of Nevada. At the 2010 United States Census, Carson City had a population of 55,274."),
            Map.of(
                "source",
                "encyclopedia",
                "passage",
                "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean that are a political division controlled by the United States. Its capital is Saipan."),
            Map.of(
                "source",
                "textbook",
                "passage",
                "Charlotte Amalie is the capital and largest city of the United States Virgin Islands. It has about 20,000 people. The city is on the island of Saint Thomas."),
            Map.of(
                "source",
                "textbook",
                "passage",
                "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district. The President of the USA and many major national government offices are in the territory. This makes it the political center of the United States of America."),
            Map.of(
                "source",
                "wiki",
                "passage",
                "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states. The federal government (including the United States military) also uses capital punishment."));

    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    CohereReranker reranker =
        CohereReranker.builder()
            .limit(3)
            .rankBy(Arrays.asList("passage", "source"))
            .apiConfig(apiConfig)
            .build();

    RerankResult result = reranker.rank(QUERY, dictDocs);

    assertNotNull(result);
    List<?> docs = result.getDocuments();
    List<Double> scores = result.getScores();

    assertEquals(3, docs.size(), "Should return 3 results");
    assertEquals(3, scores.size(), "Should return 3 scores");

    System.out.println("\n=== JAVA DICT DOCS OUTPUT ===");
    for (int i = 0; i < docs.size(); i++) {
      System.out.println(scores.get(i) + " -- " + docs.get(i));
    }

    System.out.println("\n=== EXPECTED PYTHON OUTPUT ===");
    System.out.println("0.9988121 -- {'source': 'textbook', 'passage': 'Washington, D.C. ...'}");
    System.out.println("0.5974905 -- {'source': 'wiki', 'passage': 'Capital punishment ...'}");
    System.out.println(
        "0.059101548 -- {'source': 'encyclopedia', 'passage': 'Northern Mariana ...'}");

    // Top result must be Washington D.C. with source=textbook
    @SuppressWarnings("unchecked")
    Map<String, Object> topDoc = (Map<String, Object>) docs.get(0);
    assertEquals("textbook", topDoc.get("source"), "Top result should have source=textbook");
    assertTrue(
        topDoc.get("passage").toString().contains("Washington, D.C."),
        "Top result must contain Washington D.C.");

    // Top score should be ~0.998
    double topScore = scores.get(0);
    System.out.println("\n=== SCORE COMPARISON ===");
    System.out.println("Expected top score: ~0.998");
    System.out.println("Actual top score: " + topScore);

    assertTrue(topScore > 0.9, "Top score should be > 0.9, but was: " + topScore);
    assertTrue(
        Math.abs(topScore - 0.998) < 0.05,
        "Top score should be close to 0.998, but was: " + topScore);
  }

  @Test
  void testRuntimeLimitOverride() {
    // Test that limit can be overridden at rank() time
    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    CohereReranker reranker =
        CohereReranker.builder()
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
    CohereReranker reranker =
        CohereReranker.builder()
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
  void testRuntimeRankByOverride() {
    // Test that rank_by can be overridden at rank() time
    List<Map<String, Object>> dictDocs =
        Arrays.asList(
            Map.of("title", "Doc 1", "content", "Carson City is the capital of Nevada."),
            Map.of("title", "Doc 2", "content", "Washington, D.C. is the capital of the US."));

    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    // Create reranker without rank_by
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    // Provide rank_by at runtime
    RerankResult result =
        reranker.rank(QUERY, dictDocs, Map.of("rank_by", Arrays.asList("content", "title")));

    assertNotNull(result);
    assertTrue(result.getDocuments().size() > 0, "Should return reranked results");
  }

  @Test
  void testMaxChunksPerDoc() {
    // Test max_chunks_per_doc parameter
    Map<String, String> apiConfig = Map.of("api_key", apiKey);
    CohereReranker reranker = CohereReranker.builder().apiConfig(apiConfig).build();

    // Pass max_chunks_per_doc parameter
    RerankResult result =
        reranker.rank(QUERY, STRING_DOCS, Map.of("max_chunks_per_doc", 10, "limit", 3));

    assertNotNull(result);
    assertEquals(3, result.getDocuments().size(), "Should return 3 results");
    assertTrue(result.getScores().size() > 0, "Should return scores");
  }
}
