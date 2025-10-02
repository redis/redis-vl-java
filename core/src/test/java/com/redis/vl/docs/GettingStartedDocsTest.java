package com.redis.vl.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class to verify all code examples from getting-started.adoc work correctly. This ensures the
 * documentation is accurate and the examples are not hallucinated.
 */
class GettingStartedDocsTest extends BaseIntegrationTest {

  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Clear all data
    unifiedJedis.flushAll();

    // Define schema programmatically as shown in docs
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of("name", "user-index", "prefix", "user", "storage_type", "json"),
            "fields",
            List.of(
                Map.of("name", "name", "type", "tag"),
                Map.of("name", "age", "type", "numeric"),
                Map.of("name", "job", "type", "text"),
                Map.of(
                    "name",
                    "embedding",
                    "type",
                    "vector",
                    "attrs",
                    Map.of(
                        "dims", 3,
                        "distance_metric", "cosine",
                        "algorithm", "flat",
                        "datatype", "float32"))));

    // Create index (convert Map to JSON for IndexSchema)
    try {
      ObjectMapper mapper = new ObjectMapper();
      String schemaJson = mapper.writeValueAsString(schema);
      index = new SearchIndex(IndexSchema.fromJson(schemaJson), unifiedJedis);
      index.create(true);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create index", e);
    }
  }

  @Test
  void testBasicSchemaAndIndexCreation() {
    // Verify index exists
    assertThat(index.exists()).isTrue();

    // Verify index info
    Map<String, Object> info = index.info();
    assertThat(info).isNotEmpty();
    assertThat(info.get("index_name")).isEqualTo("user-index");
  }

  @Test
  void testLoadData() {
    // Example from docs: Load data with auto-generated IDs
    List<Map<String, Object>> users =
        List.of(
            Map.of(
                "name",
                "john",
                "age",
                25,
                "job",
                "software engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "jane",
                "age",
                30,
                "job",
                "data scientist",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}),
            Map.of(
                "name",
                "joe",
                "age",
                35,
                "job",
                "product manager",
                "embedding",
                new float[] {0.7f, 0.8f, 0.9f}));

    List<String> keys = index.load(users);
    assertThat(keys).hasSize(3);

    // Verify data was loaded
    Map<String, Object> info = index.info();
    assertThat(info.get("num_docs")).isEqualTo(3L);
  }

  @Test
  void testLoadDataWithIdField() {
    // Example from docs: Load data with specified ID field
    List<Map<String, Object>> users =
        List.of(
            Map.of(
                "name",
                "john",
                "age",
                25,
                "job",
                "software engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "jane",
                "age",
                30,
                "job",
                "data scientist",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}));

    List<String> keys = index.load(users, "name");
    assertThat(keys).containsExactly("user:john", "user:jane");
  }

  @Test
  void testVectorSearch() {
    // Load test data
    List<Map<String, Object>> users =
        List.of(
            Map.of(
                "name",
                "john",
                "age",
                25,
                "job",
                "software engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "jane",
                "age",
                30,
                "job",
                "data scientist",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}),
            Map.of(
                "name",
                "joe",
                "age",
                35,
                "job",
                "product manager",
                "embedding",
                new float[] {0.7f, 0.8f, 0.9f}));
    index.load(users, "name");

    // Example from docs: Vector search
    float[] queryVector = new float[] {0.15f, 0.25f, 0.35f};
    VectorQuery query =
        VectorQuery.builder()
            .vector(queryVector)
            .field("embedding")
            .numResults(5)
            .returnFields("$.name", "$.age", "$.job")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify results
    assertThat(results).isNotEmpty();
    assertThat(results).hasSizeLessThanOrEqualTo(3);

    // Verify returned fields (JSON storage uses $.field format)
    Map<String, Object> firstResult = results.get(0);
    assertThat(firstResult).containsKeys("$.name", "$.age", "$.job", "vector_distance");

    // The closest should be john (0.1, 0.2, 0.3) to query (0.15, 0.25, 0.35)
    assertThat(firstResult.get("$.name")).isEqualTo("john");
  }

  @Test
  void testHybridQueryWithFilters() {
    // Load test data
    List<Map<String, Object>> users =
        List.of(
            Map.of(
                "name",
                "john",
                "age",
                25,
                "job",
                "software engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "jane",
                "age",
                30,
                "job",
                "data scientist",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}),
            Map.of(
                "name",
                "joe",
                "age",
                35,
                "job",
                "product manager",
                "embedding",
                new float[] {0.7f, 0.8f, 0.9f}));
    index.load(users, "name");

    // Example from docs: Hybrid query with filters
    float[] queryVector = new float[] {0.15f, 0.25f, 0.35f};

    // Create filters (use $.field for JSON storage)
    Filter ageFilter = Filter.numeric("$.age").between(20, 35);
    Filter jobFilter = Filter.text("$.job", "engineer");

    // Combine filters
    Filter combined = Filter.and(ageFilter, jobFilter);

    // Add filter to vector query
    VectorQuery hybridQuery =
        VectorQuery.builder()
            .vector(queryVector)
            .field("embedding")
            .withPreFilter(combined.build())
            .numResults(5)
            .returnFields("$.name", "$.age", "$.job")
            .build();

    List<Map<String, Object>> filteredResults = index.query(hybridQuery);

    // Verify results - should only return john (engineer, age 25)
    assertThat(filteredResults).hasSize(1);
    assertThat(filteredResults.get(0).get("$.name")).isEqualTo("john");
    assertThat(((Number) filteredResults.get(0).get("$.age")).intValue()).isEqualTo(25);
  }

  @Test
  void testFetchDocuments() {
    // Load test data
    List<Map<String, Object>> users =
        List.of(
            Map.of(
                "name",
                "john",
                "age",
                25,
                "job",
                "software engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "jane",
                "age",
                30,
                "job",
                "data scientist",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}));
    index.load(users, "name");

    // Example from docs: Fetch a single document
    Map<String, Object> user = index.fetch("user:john");
    assertThat(user).isNotNull();
    assertThat(user.get("name")).isEqualTo("john");
    assertThat(((Number) user.get("age")).intValue()).isEqualTo(25);

    // Example from docs: Fetch multiple documents (iterate individually)
    Map<String, Object> john = index.fetch("user:john");
    Map<String, Object> jane = index.fetch("user:jane");
    assertThat(john).isNotNull();
    assertThat(jane).isNotNull();
  }

  @Test
  void testIndexOperations() {
    // Example from docs: Check if index exists
    boolean exists = index.exists();
    assertThat(exists).isTrue();

    // Example from docs: Get index information
    Map<String, Object> info = index.info();
    assertThat(info).isNotEmpty();
    assertThat(info).containsKey("num_docs");

    // Load some data for delete test
    List<Map<String, Object>> users =
        List.of(
            Map.of("name", "john", "age", 25, "embedding", new float[] {0.1f, 0.2f, 0.3f}),
            Map.of("name", "jane", "age", 30, "embedding", new float[] {0.4f, 0.5f, 0.6f}));
    index.load(users, "name");

    // Example from docs: Delete specific keys
    index.dropKeys(List.of("user:john", "user:jane"));

    // Verify keys deleted
    Map<String, Object> fetchedJohn = index.fetch("user:john");
    assertThat(fetchedJohn).isNull();
  }

  @Test
  void testDeleteIndex() {
    // Load some data
    List<Map<String, Object>> users =
        List.of(Map.of("name", "john", "age", 25, "embedding", new float[] {0.1f, 0.2f, 0.3f}));
    index.load(users, "name");

    // Example from docs: Delete index without deleting data
    index.delete(false);

    // Verify index no longer exists
    assertThat(index.exists()).isFalse();

    // Verify data still exists in Redis
    Object value = unifiedJedis.jsonGet("user:john");
    assertThat(value).isNotNull();

    // Clean up
    unifiedJedis.del("user:john");
  }
}
