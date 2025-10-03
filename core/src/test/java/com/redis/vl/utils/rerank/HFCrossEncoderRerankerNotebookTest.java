package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test that exactly mirrors the 06_rerankers.ipynb notebook.
 *
 * <p>This test uses the exact same query, documents, and model as the notebook to ensure the
 * notebook will work correctly.
 *
 * <p>Tagged as "integration" because it downloads the BAAI/bge-reranker-base model (~280MB).
 */
@Tag("integration")
class HFCrossEncoderRerankerNotebookTest {

  private static HFCrossEncoderReranker crossEncoderReranker;
  private static HFCrossEncoderReranker baaiReranker;

  @BeforeAll
  static void setUp() {
    // Use BAAI model to match Python notebook exactly
    baaiReranker = HFCrossEncoderReranker.builder().model("BAAI/bge-reranker-base").build();

    assertNotNull(baaiReranker, "BAAI reranker should be initialized");
    assertEquals("BAAI/bge-reranker-base", baaiReranker.getModel(), "Should use BAAI model");

    // Also keep the default model for comparison
    crossEncoderReranker = HFCrossEncoderReranker.builder().build();

    assertNotNull(crossEncoderReranker, "Reranker should be initialized");
    assertEquals(
        "cross-encoder/ms-marco-MiniLM-L-6-v2",
        crossEncoderReranker.getModel(),
        "Should use default model");
  }

  @AfterAll
  static void tearDown() {
    if (crossEncoderReranker != null) {
      crossEncoderReranker.close();
    }
    if (baaiReranker != null) {
      baaiReranker.close();
    }
  }

  @Test
  void testNotebookSimpleReranking() {
    // Exact query and docs from notebook
    String query = "What is the capital of the United States?";

    List<String> docs =
        Arrays.asList(
            "Carson City is the capital city of the American state of Nevada. At the 2010 United States Census, Carson City had a population of 55,274.",
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean that are a political division controlled by the United States. Its capital is Saipan.",
            "Charlotte Amalie is the capital and largest city of the United States Virgin Islands. It has about 20,000 people. The city is on the island of Saint Thomas.",
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district. The President of the USA and many major national government offices are in the territory. This makes it the political center of the United States of America.",
            "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states. The federal government (including the United States military) also uses capital punishment.");

    // This should work exactly like in the notebook - using BAAI model
    RerankResult result = baaiReranker.rank(query, docs);

    // Verify results
    assertNotNull(result, "Result should not be null");
    assertNotNull(result.getDocuments(), "Documents should not be null");
    assertNotNull(result.getScores(), "Scores should not be null");

    List<?> results = result.getDocuments();
    List<Double> scores = result.getScores();

    assertEquals(3, results.size(), "Should return 3 documents (default limit)");
    assertEquals(3, scores.size(), "Should return 3 scores");

    // Verify all scores are valid
    for (Double score : scores) {
      assertNotNull(score, "Score should not be null");
      assertTrue(Double.isFinite(score), "Score should be finite");
    }

    // Verify scores are descending
    for (int i = 0; i < scores.size() - 1; i++) {
      assertTrue(
          scores.get(i) >= scores.get(i + 1),
          String.format(
              "Scores should be descending: %f >= %f at positions %d and %d",
              scores.get(i), scores.get(i + 1), i, i + 1));
    }

    // The Washington D.C. document should be ranked highly (likely first)
    String topDoc = (String) results.get(0);
    assertTrue(
        topDoc.contains("Washington, D.C.") || topDoc.contains("capital of the United States"),
        "Top result should be about Washington D.C., but was: " + topDoc);

    // Print results like notebook does
    System.out.println("\nNotebook test results (BAAI model):");
    for (int i = 0; i < results.size(); i++) {
      System.out.println(scores.get(i) + " -- " + results.get(i));
    }
  }

  @Test
  void testNotebookStructuredDocuments() {
    String query = "What is the capital of the United States?";

    // Exact structured docs from notebook
    List<Map<String, Object>> structuredDocs = new ArrayList<>();

    structuredDocs.add(
        Map.of(
            "source",
            "wiki",
            "content",
            "Carson City is the capital city of the American state of Nevada. At the 2010 United States Census, Carson City had a population of 55,274."));

    structuredDocs.add(
        Map.of(
            "source",
            "encyclopedia",
            "content",
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean that are a political division controlled by the United States. Its capital is Saipan."));

    structuredDocs.add(
        Map.of(
            "source",
            "textbook",
            "content",
            "Charlotte Amalie is the capital and largest city of the United States Virgin Islands. It has about 20,000 people. The city is on the island of Saint Thomas."));

    structuredDocs.add(
        Map.of(
            "source",
            "textbook",
            "content",
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district. The President of the USA and many major national government offices are in the territory. This makes it the political center of the United States of America."));

    structuredDocs.add(
        Map.of(
            "source",
            "wiki",
            "content",
            "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states. The federal government (including the United States military) also uses capital punishment."));

    assertEquals(5, structuredDocs.size(), "Should have 5 structured documents");

    // Rerank using BAAI model
    RerankResult structuredResult = baaiReranker.rank(query, structuredDocs);

    assertNotNull(structuredResult);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rerankedResults =
        (List<Map<String, Object>>) structuredResult.getDocuments();
    List<Double> structuredScores = structuredResult.getScores();

    assertEquals(3, rerankedResults.size(), "Should return 3 reranked documents");
    assertEquals(3, structuredScores.size(), "Should return 3 scores");

    // Verify metadata is preserved
    for (Map<String, Object> doc : rerankedResults) {
      assertTrue(doc.containsKey("source"), "Should preserve 'source' field");
      assertTrue(doc.containsKey("content"), "Should preserve 'content' field");
    }

    // Print like notebook
    System.out.println("\nNotebook structured doc results (BAAI model):");
    for (int i = 0; i < rerankedResults.size(); i++) {
      System.out.println(structuredScores.get(i) + " -- " + rerankedResults.get(i));
    }
  }

  @Test
  void testNotebookEmptyQuery() {
    List<String> docs = Arrays.asList("document one");

    // Notebook shows this should fail
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> crossEncoderReranker.rank("", docs),
            "Empty query should throw IllegalArgumentException");

    assertTrue(exception.getMessage().contains("query"), "Error message should mention 'query'");
  }

  @Test
  void testNotebookNullQuery() {
    List<String> docs = Arrays.asList("document one");

    // Notebook shows this should fail
    assertThrows(
        IllegalArgumentException.class,
        () -> crossEncoderReranker.rank(null, docs),
        "Null query should throw IllegalArgumentException");
  }
}
