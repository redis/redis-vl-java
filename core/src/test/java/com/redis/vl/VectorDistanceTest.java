package com.redis.vl;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.json.Path2;

@DisplayName("Vector Distance Tests")
class VectorDistanceTest extends BaseIntegrationTest {
  private SearchIndex index;

  @BeforeEach
  public void setup() {
    // Clean up any existing test index
    try {
      unifiedJedis.ftDropIndex("test_vector_distance");
    } catch (Exception e) {
      // Index might not exist, that's ok
    }

    // Create a test index with vector field using dict-based creation
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_vector_distance");
    indexConfig.put("storage_type", "json");
    schemaDict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of(
                "name",
                "$.embedding",
                "type",
                "vector",
                "attrs",
                Map.of("dims", 3, "algorithm", "flat", "distance_metric", "cosine")),
            Map.of("name", "$.text", "type", "text"));
    schemaDict.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, unifiedJedis);
    index.create();

    // Add test data
    addTestData();
  }

  @AfterEach
  public void cleanup() {
    if (index != null) {
      try {
        index.drop();
      } catch (Exception e) {
        // Ignore errors during cleanup
      }
    }
  }

  private void addTestData() {
    // Add 3 test documents with different embeddings
    Map<String, Object> doc1 = new HashMap<>();
    doc1.put("text", "First document");
    doc1.put("embedding", Arrays.asList(1.0f, 0.0f, 0.0f));
    unifiedJedis.jsonSetWithEscape("test:1", Path2.of("$"), doc1);

    Map<String, Object> doc2 = new HashMap<>();
    doc2.put("text", "Second document");
    doc2.put("embedding", Arrays.asList(0.0f, 1.0f, 0.0f));
    unifiedJedis.jsonSetWithEscape("test:2", Path2.of("$"), doc2);

    Map<String, Object> doc3 = new HashMap<>();
    doc3.put("text", "Third document");
    doc3.put("embedding", Arrays.asList(0.0f, 0.0f, 1.0f));
    unifiedJedis.jsonSetWithEscape("test:3", Path2.of("$"), doc3);
  }

  @Test
  public void testVectorDistanceIsReturned() {
    // Query with a vector that should match the first document closely
    VectorQuery query =
        VectorQuery.builder()
            .field("$.embedding")
            .vector(new float[] {0.9f, 0.1f, 0.1f})
            .numResults(3)
            .returnDistance(true)
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertNotNull(results, "Results should not be null");
    assertFalse(results.isEmpty(), "Results should not be empty");

    // Check that vector_distance field is present in all results
    for (Map<String, Object> result : results) {
      assertTrue(
          result.containsKey("vector_distance"),
          "Result should contain vector_distance field: " + result.keySet());

      Object distance = result.get("vector_distance");
      assertNotNull(distance, "vector_distance should not be null");

      // Vector distance should be a numeric value
      assertTrue(
          distance instanceof Number || distance instanceof String,
          "vector_distance should be numeric or string representation of number");
    }
  }

  @Test
  public void testVectorDistanceNotReturnedWhenDisabled() {
    // Query with returnDistance set to false
    VectorQuery query =
        VectorQuery.builder()
            .field("$.embedding")
            .vector(new float[] {0.9f, 0.1f, 0.1f})
            .numResults(3)
            .returnDistance(false)
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertNotNull(results, "Results should not be null");
    assertFalse(results.isEmpty(), "Results should not be empty");

    // Check that vector_distance field is NOT present
    for (Map<String, Object> result : results) {
      assertFalse(
          result.containsKey("vector_distance"),
          "Result should NOT contain vector_distance field when returnDistance is false");
    }
  }
}
