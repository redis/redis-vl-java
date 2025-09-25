package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.query.VectorRangeQuery;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.json.Path2;

/** Integration tests for pagination functionality */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Pagination Integration Tests")
class PaginationIntegrationTest extends BaseIntegrationTest {

  private static final int NUM_DOCS = 10;
  private static final List<Map<String, Object>> testData = new ArrayList<>();
  private static SearchIndex index;

  @BeforeAll
  static void setup() {
    // Create test index
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_pagination");
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
            Map.of("name", "$.credit_score", "type", "tag"),
            Map.of("name", "$.age", "type", "numeric"),
            Map.of("name", "$.job", "type", "text"));
    schemaDict.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, unifiedJedis);
    index.create(true);

    // Load test data
    loadTestData();

    // Wait for indexing to complete
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  @AfterAll
  static void cleanup() {
    if (index != null) {
      try {
        index.drop();
      } catch (Exception e) {
        // Ignore errors during cleanup
      }
    }
  }

  private static void loadTestData() {
    // Create test documents
    for (int i = 0; i < NUM_DOCS; i++) {
      Map<String, Object> doc = new HashMap<>();
      doc.put("id", "doc" + i);
      doc.put("credit_score", i < 4 ? "high" : "low");
      doc.put("age", 20 + i * 5);
      doc.put("job", i % 2 == 0 ? "engineer" : "doctor");

      // Create a simple embedding based on index
      List<Float> embedding =
          Arrays.asList((float) (0.1 * i), (float) (0.2 * i), (float) (0.3 * i));
      doc.put("embedding", embedding);

      testData.add(doc);
      unifiedJedis.jsonSetWithEscape("doc" + i, Path2.of("$"), doc);
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test paginate vector query")
  void testPaginateVectorQuery() {
    // Create a vector query
    VectorQuery vectorQuery =
        VectorQuery.builder()
            .field("$.embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .numResults(NUM_DOCS)
            .returnFields("id", "credit_score", "age", "job")
            .build();

    int batchSize = 2;
    List<Map<String, Object>> allResults = new ArrayList<>();
    int iterations = 0;

    // Paginate through results
    for (List<Map<String, Object>> batch : index.paginate(vectorQuery, batchSize)) {
      iterations++;
      assertThat(batch).hasSizeLessThanOrEqualTo(batchSize);
      allResults.addAll(batch);
    }

    // Verify we got all results
    int expectedTotal = NUM_DOCS;
    int expectedIterations = (expectedTotal + batchSize - 1) / batchSize; // Ceiling division

    assertThat(allResults).hasSize(expectedTotal);
    assertThat(iterations).isEqualTo(expectedIterations);
  }

  @Test
  @Order(2)
  @DisplayName("Test paginate filter query")
  void testPaginateFilterQuery() {
    // Create a filter query for high credit score - field name must match schema definition
    Filter filter = Filter.tag("$.credit_score", "high");

    int batchSize = 3;
    List<Map<String, Object>> allResults = new ArrayList<>();
    int iterations = 0;

    // Paginate through results
    for (List<Map<String, Object>> batch : index.paginate(filter, batchSize)) {
      iterations++;
      assertThat(batch).hasSizeLessThanOrEqualTo(batchSize);
      allResults.addAll(batch);
    }

    // Verify we got the expected results
    int expectedCount = 4; // Based on our test data setup
    int expectedIterations = (expectedCount + batchSize - 1) / batchSize;

    assertThat(allResults).hasSize(expectedCount);
    assertThat(iterations).isEqualTo(expectedIterations);

    // Verify all results have high credit score
    for (Map<String, Object> result : allResults) {
      assertThat(result.get("$.credit_score")).isEqualTo("high");
    }
  }

  @Test
  @Order(3)
  @DisplayName("Test paginate range query")
  void testPaginateRangeQuery() {
    // Create a range query with a threshold that will capture multiple documents
    // Using a reference vector close to our test data
    VectorRangeQuery rangeQuery =
        VectorRangeQuery.builder()
            .field("$.embedding")
            .vector(new float[] {0.5f, 1.0f, 1.5f}) // Middle of our test data range
            .distanceThreshold(5.0) // Large enough to capture multiple docs
            .build();

    int batchSize = 2;
    List<Map<String, Object>> allResults = new ArrayList<>();
    int iterations = 0;

    // Paginate through results
    for (List<Map<String, Object>> batch : index.paginate(rangeQuery, batchSize)) {
      iterations++;
      assertThat(batch).hasSizeLessThanOrEqualTo(batchSize);
      allResults.addAll(batch);

      // For range query, check distance threshold
      for (Map<String, Object> result : batch) {
        if (result.containsKey("vector_distance")) {
          double distance = Double.parseDouble(result.get("vector_distance").toString());
          assertThat(distance).isLessThanOrEqualTo(5.0); // Should match our threshold
        }
      }
    }

    // Verify we got results
    assertThat(allResults).isNotEmpty();
    assertThat(iterations).isGreaterThan(0);

    // Verify batch sizes were respected
    if (allResults.size() > batchSize) {
      assertThat(iterations).isGreaterThanOrEqualTo(allResults.size() / batchSize);
    }
  }

  @Test
  @Order(4)
  @DisplayName("Test paginate with small batch size")
  void testPaginateWithSmallBatchSize() {
    Filter query = Filter.numeric("$.age").between(20, 50);

    int batchSize = 1;
    List<Map<String, Object>> allResults = new ArrayList<>();

    for (List<Map<String, Object>> batch : index.paginate(query, batchSize)) {
      assertThat(batch).hasSize(1); // Each batch should have exactly 1 item
      allResults.addAll(batch);
    }

    // Should get multiple results based on age filter
    assertThat(allResults.size()).isGreaterThan(1);
  }

  @Test
  @Order(5)
  @DisplayName("Test paginate with large batch size")
  void testPaginateWithLargeBatchSize() {
    Filter query = Filter.text("$.job", "engineer");

    int batchSize = 100; // Larger than our dataset
    List<Map<String, Object>> allResults = new ArrayList<>();
    int iterations = 0;

    for (List<Map<String, Object>> batch : index.paginate(query, batchSize)) {
      iterations++;
      allResults.addAll(batch);
    }

    // Should complete in a single iteration
    assertThat(iterations).isEqualTo(1);
    assertThat(allResults).isNotEmpty();
  }

  @Test
  @Order(6)
  @DisplayName("Test paginate with no results")
  void testPaginateWithNoResults() {
    // Query that should return no results
    Filter query = Filter.tag("$.credit_score", "nonexistent");

    int batchSize = 5;
    List<Map<String, Object>> allResults = new ArrayList<>();

    for (List<Map<String, Object>> batch : index.paginate(query, batchSize)) {
      allResults.addAll(batch);
    }

    // Should get no results
    assertThat(allResults).isEmpty();
  }
}
