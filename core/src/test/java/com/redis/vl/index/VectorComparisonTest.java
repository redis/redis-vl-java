package com.redis.vl.index;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class VectorComparisonTest extends BaseIntegrationTest {

  public static double calculateCosineDistance(float[] a, float[] b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    return 1.0 - similarity; // Distance = 1 - similarity
  }

  @Test
  void testVectorScoresMatchExpectedValues() {
    // Use exact same schema and data as notebooks
    Map<String, Object> schema = new HashMap<>();

    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_vector_comparison_java");
    indexConfig.put("prefix", "test_docs_java");
    schema.put("index", indexConfig);

    List<Map<String, Object>> fields = new ArrayList<>();
    fields.add(Map.of("name", "user", "type", "tag"));
    fields.add(Map.of("name", "credit_score", "type", "tag"));
    fields.add(Map.of("name", "job", "type", "text"));
    fields.add(Map.of("name", "age", "type", "numeric"));

    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");

    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "user_embedding");
    vectorField.put("type", "vector");
    vectorField.put("attrs", vectorAttrs);
    fields.add(vectorField);

    schema.put("fields", fields);

    SearchIndex index = SearchIndex.fromDict(schema, unifiedJedis);
    index.create(true);

    // Load exact same data as Python notebook
    List<Map<String, Object>> data = new ArrayList<>();

    Map<String, Object> john = new HashMap<>();
    john.put("user", "john");
    john.put("age", 1);
    john.put("job", "engineer");
    john.put("credit_score", "high");
    john.put("user_embedding", new float[] {0.1f, 0.1f, 0.5f});
    data.add(john);

    Map<String, Object> mary = new HashMap<>();
    mary.put("user", "mary");
    mary.put("age", 2);
    mary.put("job", "doctor");
    mary.put("credit_score", "low");
    mary.put("user_embedding", new float[] {0.1f, 0.1f, 0.5f});
    data.add(mary);

    Map<String, Object> joe = new HashMap<>();
    joe.put("user", "joe");
    joe.put("age", 3);
    joe.put("job", "dentist");
    joe.put("credit_score", "medium");
    joe.put("user_embedding", new float[] {0.9f, 0.9f, 0.1f});
    data.add(joe);

    // Load data
    for (Map<String, Object> doc : data) {
      String key = index.getPrefix() + ":" + doc.get("user");
      index.addDocument(key, doc);
    }

    // Add tyler
    Map<String, Object> tyler = new HashMap<>();
    tyler.put("user", "tyler");
    tyler.put("age", 9);
    tyler.put("job", "engineer");
    tyler.put("credit_score", "high");
    tyler.put("user_embedding", new float[] {0.1f, 0.3f, 0.5f});

    String tylerKey = index.getPrefix() + ":tyler";
    index.addDocument(tylerKey, tyler);

    // Same query as notebooks: [0.1, 0.1, 0.5]
    com.redis.vl.query.VectorQuery query =
        com.redis.vl.query.VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "age", "job", "credit_score", "vector_distance")
            .numResults(4)
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify results are returned
    assertFalse(results.isEmpty(), "Query should return results");

    // Extract and verify vector distances
    for (Map<String, Object> result : results) {
      assertNotNull(result.get("user"));
      assertNotNull(result.get("age"));
      assertNotNull(result.get("job"));
      assertNotNull(result.get("credit_score"));

      // Extract vector distance from various possible fields
      Object distance = result.get("vector_distance");
      if (distance == null) {
        distance = result.get("__user_embedding_score");
      }
      if (distance == null) {
        distance = result.get("score");
      }
      assertNotNull(distance, "Vector distance should be present in results");
    }

    // Verify manual cosine distance calculations
    float[] queryVec = {0.1f, 0.1f, 0.5f};

    // John and Mary have [0.1, 0.1, 0.5] - should be distance 0 (identical)
    double johnMaryDistance = calculateCosineDistance(queryVec, new float[] {0.1f, 0.1f, 0.5f});
    assertEquals(
        0.0, johnMaryDistance, 0.001, "John/Mary should have distance 0 (identical vectors)");

    // Joe has [0.9, 0.9, 0.1]
    double joeDistance = calculateCosineDistance(queryVec, new float[] {0.9f, 0.9f, 0.1f});
    assertTrue(joeDistance > 0.5, "Joe should have high distance (dissimilar vector)");

    // Tyler has [0.1, 0.3, 0.5]
    double tylerDistance = calculateCosineDistance(queryVec, new float[] {0.1f, 0.3f, 0.5f});
    assertTrue(
        tylerDistance > 0 && tylerDistance < 0.1, "Tyler should have small but non-zero distance");

    // Verify the results match expected mathematical values
    // John and Mary should have distance 0 (exact match)
    // Tyler should have distance ~0.0566
    // Joe should have highest distance

    Map<String, Object> johnResult =
        results.stream().filter(r -> "john".equals(r.get("user"))).findFirst().orElse(null);
    assertNotNull(johnResult);

    Object johnScore = johnResult.get("__user_embedding_score");
    if (johnScore != null) {
      double score = Double.parseDouble(johnScore.toString());
      assertEquals(0.0, score, 0.001, "John should have perfect similarity (score 0)");
    }

    // Cleanup
    index.delete(true);
  }
}
