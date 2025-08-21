package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;

@DisplayName("Hash vs JSON Storage Integration Tests")
class HashVsJsonIntegrationTest extends BaseIntegrationTest {

  private UnifiedJedis jedis;
  private SearchIndex hashIndex;
  private SearchIndex jsonIndex;
  private List<Map<String, Object>> testData;

  @BeforeEach
  void setup() {
    jedis = unifiedJedis;

    // Create test data matching the notebook
    testData = createTestData();
  }

  @AfterEach
  void cleanup() {
    if (hashIndex != null) {
      try {
        hashIndex.delete(true);
      } catch (Exception e) {
        // Ignore
      }
    }
    if (jsonIndex != null) {
      try {
        jsonIndex.delete(true);
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  private List<Map<String, Object>> createTestData() {
    List<Map<String, Object>> data = new ArrayList<>();

    // Create byte array for vector (3-dimensional)
    byte[] embedding1 = floatsToBytes(new float[] {0.1f, 0.1f, 0.5f});
    byte[] embedding2 = floatsToBytes(new float[] {0.1f, 0.1f, 0.5f});
    byte[] embedding3 = floatsToBytes(new float[] {0.9f, 0.9f, 0.1f});

    data.add(
        Map.of(
            "user", "john",
            "age", 18,
            "job", "engineer",
            "credit_score", "high",
            "office_location", "-122.4194,37.7749",
            "user_embedding", embedding1,
            "last_updated", 1741627789));

    data.add(
        Map.of(
            "user", "mary",
            "age", 2,
            "job", "doctor",
            "credit_score", "low",
            "office_location", "-122.4194,37.7749",
            "user_embedding", embedding2,
            "last_updated", 1741627789));

    data.add(
        Map.of(
            "user", "joe",
            "age", 35,
            "job", "dentist",
            "credit_score", "medium",
            "office_location", "-122.0839,37.3861",
            "user_embedding", embedding3,
            "last_updated", 1742232589));

    return data;
  }

  private byte[] floatsToBytes(float[] floats) {
    ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    for (float f : floats) {
      buffer.putFloat(f);
    }
    return buffer.array();
  }

  @Test
  @DisplayName("Test hash storage with vector query and filters")
  void testHashStorageWithVectorQuery() {
    // Create hash schema
    Map<String, Object> hashSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "test-user-hash",
                    "prefix", "test-user-hash-docs",
                    "storage_type", "hash"),
            "fields",
                List.of(
                    Map.of("name", "user", "type", "tag"),
                    Map.of("name", "credit_score", "type", "tag"),
                    Map.of("name", "job", "type", "text"),
                    Map.of("name", "age", "type", "numeric"),
                    Map.of("name", "office_location", "type", "geo"),
                    Map.of(
                        "name", "user_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    hashIndex = SearchIndex.fromDict(hashSchema, jedis);
    hashIndex.create(true);

    // Load data
    List<String> keys = hashIndex.load(testData, "user");
    assertThat(keys).hasSize(3);

    // Wait for indexing
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Test 1: Simple vector query without filters
    VectorQuery simpleQuery =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "job", "age", "credit_score")
            .numResults(3)
            .build();

    List<Map<String, Object>> simpleResults = hashIndex.query(simpleQuery);

    // Verify we got results
    assertThat(simpleResults).isNotEmpty();
    assertThat(simpleResults).hasSizeGreaterThanOrEqualTo(2);

    // Test 2: Vector query with filters
    Filter creditFilter = Filter.tag("credit_score", "high");
    Filter jobFilter = Filter.prefix("job", "enginee");
    Filter ageFilter = Filter.numeric("age").gt(17);
    Filter combinedFilter = Filter.and(creditFilter, jobFilter, ageFilter);

    VectorQuery filteredQuery =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "office_location")
            .withPreFilter(combinedFilter.build())
            .numResults(10)
            .build();

    List<Map<String, Object>> filteredResults = hashIndex.query(filteredQuery);

    // Should find john (age=18, job=engineer, credit_score=high)
    assertThat(filteredResults).isNotEmpty();
    assertThat(filteredResults.get(0).get("user")).isEqualTo("john");
  }

  @Test
  @DisplayName("Test JSON storage with vector query")
  void testJsonStorageWithVectorQuery() {
    // Create JSON schema
    Map<String, Object> jsonSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "test-user-json",
                    "prefix", "test-user-json-docs",
                    "storage_type", "json"),
            "fields",
                List.of(
                    Map.of("name", "user", "type", "tag"),
                    Map.of("name", "credit_score", "type", "tag"),
                    Map.of("name", "job", "type", "text"),
                    Map.of("name", "age", "type", "numeric"),
                    Map.of("name", "office_location", "type", "geo"),
                    Map.of(
                        "name", "user_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    jsonIndex = SearchIndex.fromDict(jsonSchema, jedis);
    jsonIndex.create(true);

    // Convert data for JSON (float arrays instead of byte arrays)
    List<Map<String, Object>> jsonData = new ArrayList<>();
    for (Map<String, Object> user : testData) {
      Map<String, Object> jsonUser = new HashMap<>(user);

      // Convert byte array to float array
      Object embedding = user.get("user_embedding");
      if (embedding instanceof byte[]) {
        byte[] embBytes = (byte[]) embedding;
        ByteBuffer buffer = ByteBuffer.wrap(embBytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[3];
        for (int i = 0; i < 3; i++) {
          floats[i] = buffer.getFloat();
        }
        jsonUser.put("user_embedding", floats);
      }

      jsonData.add(jsonUser);
    }

    // Load data
    List<String> keys = jsonIndex.load(jsonData, "user");
    assertThat(keys).hasSize(3);

    // Wait for indexing
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Test vector query
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "job", "age", "credit_score")
            .numResults(3)
            .build();

    List<Map<String, Object>> results = jsonIndex.query(query);
    assertThat(results).isNotEmpty();
    assertThat(results).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("Test nested JSON with path support")
  void testNestedJsonWithPaths() {
    // Create bike data with nested metadata
    List<Map<String, Object>> bikeData = new ArrayList<>();

    Map<String, Object> bike1 = new HashMap<>();
    bike1.put("name", "Specialized Stumpjumper");
    bike1.put(
        "metadata",
        Map.of(
            "model", "Stumpjumper",
            "brand", "Specialized",
            "type", "Enduro bikes",
            "price", 3000));
    bike1.put("description", "Versatile enduro bike");
    // Use a simple embedding for testing
    bike1.put("bike_embedding", new float[] {0.1f, 0.2f, 0.3f});

    Map<String, Object> bike2 = new HashMap<>();
    bike2.put("name", "Trek Slash");
    bike2.put(
        "metadata",
        Map.of(
            "model", "Slash",
            "brand", "Trek",
            "type", "Enduro bikes",
            "price", 5000));
    bike2.put("description", "Aggressive enduro bike");
    bike2.put("bike_embedding", new float[] {0.4f, 0.5f, 0.6f});

    bikeData.add(bike1);
    bikeData.add(bike2);

    // Create schema with nested paths
    Map<String, Object> bikeSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "test-bike-json",
                    "prefix", "test-bike-json",
                    "storage_type", "json"),
            "fields",
                List.of(
                    Map.of(
                        "name", "model",
                        "type", "tag",
                        "path", "$.metadata.model"),
                    Map.of(
                        "name", "brand",
                        "type", "tag",
                        "path", "$.metadata.brand"),
                    Map.of(
                        "name", "price",
                        "type", "numeric",
                        "path", "$.metadata.price"),
                    Map.of(
                        "name", "bike_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    SearchIndex bikeIndex = SearchIndex.fromDict(bikeSchema, jedis);
    bikeIndex.create(true);

    // Load data
    List<String> keys = bikeIndex.load(bikeData);
    assertThat(keys).hasSize(2);

    // Wait for indexing
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Test vector query
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.3f, 0.4f, 0.5f})
            .field("bike_embedding")
            .returnFields("brand", "name", "$.metadata.type")
            .numResults(2)
            .build();

    List<Map<String, Object>> results = bikeIndex.query(query);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSizeGreaterThanOrEqualTo(1);

    // Clean up
    bikeIndex.delete(true);
  }

  @Test
  @DisplayName("Debug field names in queries")
  void debugFieldNamesInQueries() {
    // Let's debug what field names are being used
    Map<String, Object> hashSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "debug-hash",
                    "prefix", "debug-hash",
                    "storage_type", "hash"),
            "fields",
                List.of(
                    Map.of("name", "user", "type", "tag"),
                    Map.of("name", "age", "type", "numeric")));

    SearchIndex debugIndex = SearchIndex.fromDict(hashSchema, jedis);
    debugIndex.create(true);

    // Check the actual field names in the schema
    IndexSchema schema = debugIndex.getSchema();
    assertThat(schema.getStorageType().toString().toLowerCase()).isEqualTo("hash");
    assertThat(schema.getFields()).hasSize(2);
    assertThat(schema.getFields().get(0).getName()).isEqualTo("user");
    assertThat(schema.getFields().get(1).getName()).isEqualTo("age");

    // Load simple data
    List<Map<String, Object>> simpleData =
        List.of(Map.of("user", "test1", "age", 25), Map.of("user", "test2", "age", 30));

    debugIndex.load(simpleData);

    // Wait for indexing
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Try different queries
    Filter ageQuery = Filter.numeric("age").gt(20);
    // The filter format includes parenthesis around the range
    assertThat(ageQuery.build()).contains("@age:[(20 +inf]");

    // Clean up
    debugIndex.delete(true);
  }
}
