package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.SearchResult;

/** Integration tests for batch operations with batch_size parameter */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Batch Operations Integration Tests")
class BatchOperationsIntegrationTest extends BaseIntegrationTest {

  private static SearchIndex index;
  private static final int NUM_DOCS = 20;

  @BeforeAll
  static void setup() {
    // Create test index
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_batch_ops");
    indexConfig.put("storage_type", "json");
    indexConfig.put("prefix", "batch:");
    schemaDict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of(
                "name", "$.category",
                "type", "tag"),
            Map.of(
                "name", "$.price",
                "type", "numeric"),
            Map.of(
                "name", "$.description",
                "type", "text"),
            Map.of(
                "name", "$.rating",
                "type", "numeric"));
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
        // Clean up test documents
        for (int i = 0; i < NUM_DOCS; i++) {
          unifiedJedis.del("batch:doc" + i);
        }
        index.drop();
      } catch (Exception e) {
        // Ignore errors during cleanup
      }
    }
  }

  private static void loadTestData() {
    String[] categories = {"electronics", "books", "clothing", "food"};
    String[] descriptions = {"great product", "excellent quality", "good value", "nice item"};

    for (int i = 0; i < NUM_DOCS; i++) {
      Map<String, Object> doc = new HashMap<>();
      doc.put("id", "doc" + i);
      doc.put("category", categories[i % categories.length]);
      doc.put("price", 10.0 + i * 5);
      doc.put("description", descriptions[i % descriptions.length]);
      doc.put("rating", 1 + (i % 5));

      unifiedJedis.jsonSetWithEscape("batch:doc" + i, Path2.of("$"), doc);
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test batch search with multiple batches")
  void testBatchSearchWithMultipleBatches() {
    // Create multiple search queries (using JSONPath notation for JSON storage)
    List<String> queries =
        Arrays.asList(
            "@\\$\\.category:{electronics}",
            "@\\$\\.price:[10 50]",
            "@\\$\\.description:(excellent)",
            "@\\$\\.rating:[4 5]",
            "@\\$\\.category:{books}",
            "@\\$\\.price:[100 200]");

    int batchSize = 2; // Process 2 queries at a time

    // Execute batch search
    List<SearchResult> results = index.batchSearch(queries, batchSize);

    // Verify we got results for all queries
    assertThat(results).hasSize(queries.size());

    // Verify each result is not null
    for (SearchResult result : results) {
      assertThat(result).isNotNull();
    }

    // Verify the first query returns electronics
    SearchResult electronicsResult = results.get(0);
    assertThat(electronicsResult.getTotalResults()).isGreaterThan(0);
  }

  @Test
  @Order(2)
  @DisplayName("Test batch query with multiple batches")
  void testBatchQueryWithMultipleBatches() {
    // Create multiple filter queries (using JSONPath for JSON storage)
    List<Filter> queries =
        Arrays.asList(
            Filter.tag("$.category", "electronics"),
            Filter.numeric("$.price").between(10, 50),
            Filter.text("$.description", "excellent"),
            Filter.numeric("$.rating").gt(3),
            Filter.tag("$.category", "books", "food"),
            Filter.numeric("$.price").lt(30));

    int batchSize = 3; // Process 3 queries at a time

    // Execute batch query
    List<List<Map<String, Object>>> results = index.batchQuery(queries, batchSize);

    // Verify we got results for all queries
    assertThat(results).hasSize(queries.size());

    // Verify each query returned a list of results
    for (List<Map<String, Object>> queryResults : results) {
      assertThat(queryResults).isNotNull();
    }

    // Verify specific query results
    List<Map<String, Object>> electronicsResults = results.get(0);
    for (Map<String, Object> doc : electronicsResults) {
      assertThat(doc.get("$.category")).isEqualTo("electronics");
    }
  }

  @Test
  @Order(3)
  @DisplayName("Test batch search with batch size of 1")
  void testBatchSearchWithBatchSizeOne() {
    List<String> queries = Arrays.asList("@category:{clothing}", "@price:[0 100]", "@rating:[1 3]");

    int batchSize = 1; // Process one query at a time

    List<SearchResult> results = index.batchSearch(queries, batchSize);

    assertThat(results).hasSize(queries.size());

    // Each query should be processed individually
    for (SearchResult result : results) {
      assertThat(result).isNotNull();
    }
  }

  @Test
  @Order(4)
  @DisplayName("Test batch query with large batch size")
  void testBatchQueryWithLargeBatchSize() {
    // Create a list of queries
    List<Filter> queries = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      queries.add(Filter.numeric("rating").eq(i));
    }

    int batchSize = 100; // Larger than number of queries

    List<List<Map<String, Object>>> results = index.batchQuery(queries, batchSize);

    // Should still process all queries
    assertThat(results).hasSize(queries.size());

    // Verify rating values match query
    for (int i = 0; i < results.size(); i++) {
      List<Map<String, Object>> queryResults = results.get(i);
      int expectedRating = i + 1;

      for (Map<String, Object> doc : queryResults) {
        if (doc.containsKey("$.rating")) {
          assertThat(((Number) doc.get("$.rating")).intValue()).isEqualTo(expectedRating);
        }
      }
    }
  }

  @Test
  @Order(5)
  @DisplayName("Test batch operations with empty query list")
  void testBatchOperationsWithEmptyList() {
    List<String> emptySearchQueries = new ArrayList<>();
    List<Filter> emptyFilterQueries = new ArrayList<>();

    List<SearchResult> searchResults = index.batchSearch(emptySearchQueries, 5);
    List<List<Map<String, Object>>> queryResults = index.batchQuery(emptyFilterQueries, 5);

    assertThat(searchResults).isEmpty();
    assertThat(queryResults).isEmpty();
  }

  @Test
  @Order(6)
  @DisplayName("Test batch search performance with different batch sizes")
  void testBatchSearchPerformance() {
    // Create a larger set of queries
    List<String> queries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      queries.add("@price:[" + (i * 10) + " " + ((i + 1) * 10) + "]");
    }

    // Test with different batch sizes
    int[] batchSizes = {1, 2, 5, 10};

    for (int batchSize : batchSizes) {
      long startTime = System.currentTimeMillis();
      List<SearchResult> results = index.batchSearch(queries, batchSize);
      long endTime = System.currentTimeMillis();

      assertThat(results).hasSize(queries.size());
    }
  }
}
