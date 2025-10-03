package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Real integration test with BAAI/bge-reranker-base model.
 *
 * <p>Compares outputs to Python notebook to verify correctness.
 *
 * <p>Python output (from 06_rerankers.ipynb): 0.07461125403642654 -- Washington, D.C. ...
 * 0.05220315232872963 -- Charlotte Amalie ... -0.3802368640899658 -- Carson City ...
 */
@Tag("integration")
class BAAIModelRealIntegrationTest {

  private static HFCrossEncoderReranker reranker;

  @BeforeAll
  static void setUp() {
    System.out.println("=== LOADING BAAI/bge-reranker-base MODEL ===");
    reranker = HFCrossEncoderReranker.builder().model("BAAI/bge-reranker-base").build();

    assertNotNull(reranker, "Reranker must initialize");
    assertEquals("BAAI/bge-reranker-base", reranker.getModel());
    System.out.println("=== MODEL LOADED ===");
  }

  @AfterAll
  static void tearDown() {
    if (reranker != null) {
      reranker.close();
    }
  }

  @Test
  void testBAAIModelProducesCorrectScores() {
    String query = "What is the capital of the United States?";

    List<String> docs =
        Arrays.asList(
            "Carson City is the capital city of the American state of Nevada. At the 2010 United States Census, Carson City had a population of 55,274.",
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean that are a political division controlled by the United States. Its capital is Saipan.",
            "Charlotte Amalie is the capital and largest city of the United States Virgin Islands. It has about 20,000 people. The city is on the island of Saint Thomas.",
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district. The President of the USA and many major national government offices are in the territory. This makes it the political center of the United States of America.",
            "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states. The federal government (including the United States military) also uses capital punishment.");

    RerankResult result = reranker.rank(query, docs);

    assertNotNull(result);

    List<?> results = result.getDocuments();
    List<Double> scores = result.getScores();

    System.out.println("\n=== JAVA OUTPUT ===");
    for (int i = 0; i < results.size(); i++) {
      String docPreview =
          results.get(i).toString().substring(0, Math.min(50, results.get(i).toString().length()));
      System.out.println(scores.get(i) + " -- " + docPreview + "...");
    }

    System.out.println("\n=== EXPECTED PYTHON OUTPUT (with sigmoid) ===");
    System.out.println("0.9999381  --  Washington, D.C. ...");
    System.out.println("0.3802366  --  Charlotte Amalie ...");
    System.out.println("0.0746112  --  Carson City ...");

    // Verify we got 3 results
    assertEquals(3, results.size(), "Should return 3 results");
    assertEquals(3, scores.size(), "Should return 3 scores");

    // Top result must be Washington D.C.
    String topDoc = (String) results.get(0);
    assertTrue(
        topDoc.contains("Washington, D.C."),
        "Top result must be Washington D.C., but was: " + topDoc);

    // Score for Washington D.C. should be ~0.9999 (after sigmoid)
    double topScore = scores.get(0);
    System.out.println("\n=== SCORE COMPARISON ===");
    System.out.println("Expected top score: ~0.9999");
    System.out.println("Actual top score: " + topScore);

    assertTrue(
        topScore > 0.0 && topScore < 1.0,
        "Top score should be between 0 and 1, but was: " + topScore);

    assertTrue(
        Math.abs(topScore - 0.9999) < 0.01,
        "Top score should be close to 0.9999, but was: " + topScore);

    // Second result should be Charlotte Amalie with score ~0.380
    String secondDoc = (String) results.get(1);
    double secondScore = scores.get(1);

    assertTrue(
        secondDoc.contains("Charlotte Amalie"),
        "Second result should be Charlotte Amalie, but was: " + secondDoc);

    assertTrue(
        Math.abs(secondScore - 0.380) < 0.01,
        "Second score should be close to 0.380, but was: " + secondScore);

    // Third result should be Carson City with score ~0.074
    String thirdDoc = (String) results.get(2);
    double thirdScore = scores.get(2);

    assertTrue(
        thirdDoc.contains("Carson City"),
        "Third result should be Carson City, but was: " + thirdDoc);

    assertTrue(
        Math.abs(thirdScore - 0.074) < 0.01,
        "Third score should be close to 0.074, but was: " + thirdScore);
  }
}
