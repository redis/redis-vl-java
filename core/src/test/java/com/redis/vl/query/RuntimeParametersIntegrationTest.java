package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for runtime parameters support (Python PR #439).
 *
 * <p>Tests runtime parameter support for HNSW and SVS-VAMANA vector indexes:
 *
 * <ul>
 *   <li>efRuntime - HNSW dynamic candidate list size
 *   <li>epsilon - Range search approximation factor
 *   <li>searchWindowSize - SVS-VAMANA KNN search window
 *   <li>useSearchHistory - SVS-VAMANA search buffer control
 *   <li>searchBufferCapacity - SVS-VAMANA compression tuning
 * </ul>
 *
 * <p>Python reference: redis-vl-python PR #439
 */
@DisplayName("Runtime Parameters Integration Tests")
class RuntimeParametersIntegrationTest extends BaseIntegrationTest {

  private static final String HNSW_PREFIX = "hnsw_" + UUID.randomUUID().toString().substring(0, 8);
  private SearchIndex hnswIndex;

  private static final float[] VECTOR1 = {0.1f, 0.2f, 0.3f};
  private static final float[] VECTOR2 = {0.2f, 0.3f, 0.4f};
  private static final float[] VECTOR3 = {0.9f, 0.8f, 0.7f};

  @BeforeEach
  void setUp() {
    // Create HNSW index schema
    Map<String, Object> hnswSchemaDict = new HashMap<>();
    Map<String, Object> hnswIndexConfig = new HashMap<>();
    hnswIndexConfig.put("name", "runtime_params_hnsw_" + HNSW_PREFIX);
    hnswIndexConfig.put("prefix", "doc_hnsw_" + HNSW_PREFIX);
    hnswIndexConfig.put("storage_type", "hash");
    hnswSchemaDict.put("index", hnswIndexConfig);

    List<Map<String, Object>> hnswFields =
        Arrays.asList(
            Map.of("name", "text", "type", "text"),
            Map.of(
                "name",
                "embedding",
                "type",
                "vector",
                "attrs",
                Map.of(
                    "dims",
                    3,
                    "distance_metric",
                    "cosine",
                    "algorithm",
                    "hnsw",
                    "m",
                    16,
                    "ef_construction",
                    200)));
    hnswSchemaDict.put("fields", hnswFields);

    IndexSchema hnswSchema = IndexSchema.fromDict(hnswSchemaDict);
    hnswIndex = new SearchIndex(hnswSchema, unifiedJedis);
    hnswIndex.create();

    // Load test data
    List<Map<String, Object>> hnswDocs =
        Arrays.asList(
            Map.of("text", "first document", "embedding", VECTOR1),
            Map.of("text", "second document", "embedding", VECTOR2),
            Map.of("text", "third document", "embedding", VECTOR3));

    hnswIndex.load(hnswDocs);
  }

  @AfterEach
  void tearDown() {
    if (hnswIndex != null) {
      hnswIndex.delete(true);
    }
  }

  // ===================================================================================
  // VectorQuery Runtime Parameters Tests
  // ===================================================================================

  @Test
  @DisplayName("VectorQuery: Should support efRuntime parameter for HNSW")
  void testVectorQueryWithEfRuntime() {
    // Test with default (no ef_runtime)
    VectorQuery queryDefault =
        VectorQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .numResults(3)
            .returnDistance(true)
            .build();

    List<Map<String, Object>> resultsDefault = hnswIndex.query(queryDefault);
    assertThat(resultsDefault).isNotEmpty();

    // Test with ef_runtime=50 (lower recall, faster)
    VectorQuery queryLowEf =
        VectorQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .numResults(3)
            .returnDistance(true)
            .efRuntime(50) // Lower ef_runtime
            .build();

    List<Map<String, Object>> resultsLowEf = hnswIndex.query(queryLowEf);
    assertThat(resultsLowEf).isNotEmpty();

    // Test with ef_runtime=200 (higher recall, slower)
    VectorQuery queryHighEf =
        VectorQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .numResults(3)
            .returnDistance(true)
            .efRuntime(200) // Higher ef_runtime
            .build();

    List<Map<String, Object>> resultsHighEf = hnswIndex.query(queryHighEf);
    assertThat(resultsHighEf).isNotEmpty();

    // All queries should return results, potentially with different recall
    assertThat(resultsDefault).hasSameSizeAs(resultsLowEf);
    assertThat(resultsDefault).hasSameSizeAs(resultsHighEf);
  }

  @Test
  @DisplayName("VectorQuery: Should validate efRuntime must be positive")
  void testVectorQueryEfRuntimeValidation() {
    // Test that efRuntime must be positive
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .efRuntime(0) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("efRuntime must be positive");

    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .efRuntime(-10) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("efRuntime must be positive");
  }

  @Test
  @DisplayName("VectorQuery: Should support searchWindowSize parameter for SVS-VAMANA")
  void testVectorQueryWithSearchWindowSize() {
    // Note: This test will pass with HNSW (parameter ignored) but demonstrates API
    VectorQuery query =
        VectorQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .numResults(3)
            .searchWindowSize(40) // SVS-VAMANA parameter
            .build();

    List<Map<String, Object>> results = hnswIndex.query(query);
    assertThat(results).isNotEmpty();
  }

  @Test
  @DisplayName("VectorQuery: Should validate searchWindowSize must be positive")
  void testVectorQuerySearchWindowSizeValidation() {
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .searchWindowSize(0) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchWindowSize must be positive");

    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .searchWindowSize(-5) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchWindowSize must be positive");
  }

  @Test
  @DisplayName("VectorQuery: Should support useSearchHistory parameter")
  void testVectorQueryWithUseSearchHistory() {
    // Test all valid values: OFF, ON, AUTO
    String[] validValues = {"OFF", "ON", "AUTO"};

    for (String value : validValues) {
      VectorQuery query =
          VectorQuery.builder()
              .vector(VECTOR1)
              .field("embedding")
              .numResults(3)
              .useSearchHistory(value)
              .build();

      List<Map<String, Object>> results = hnswIndex.query(query);
      assertThat(results).isNotEmpty();
    }
  }

  @Test
  @DisplayName("VectorQuery: Should validate useSearchHistory allowed values")
  void testVectorQueryUseSearchHistoryValidation() {
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .useSearchHistory("INVALID") // Invalid value
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("useSearchHistory must be one of: OFF, ON, AUTO");

    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .useSearchHistory("on") // Case-sensitive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("useSearchHistory must be one of: OFF, ON, AUTO");
  }

  @Test
  @DisplayName("VectorQuery: Should support searchBufferCapacity parameter")
  void testVectorQueryWithSearchBufferCapacity() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .numResults(3)
            .searchBufferCapacity(100) // SVS-VAMANA parameter
            .build();

    List<Map<String, Object>> results = hnswIndex.query(query);
    assertThat(results).isNotEmpty();
  }

  @Test
  @DisplayName("VectorQuery: Should validate searchBufferCapacity must be positive")
  void testVectorQuerySearchBufferCapacityValidation() {
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .searchBufferCapacity(0) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchBufferCapacity must be positive");

    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .numResults(3)
                    .searchBufferCapacity(-10) // Invalid: must be positive
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchBufferCapacity must be positive");
  }

  // ===================================================================================
  // VectorRangeQuery Runtime Parameters Tests
  // ===================================================================================

  @Test
  @DisplayName("VectorRangeQuery: Should support epsilon parameter")
  void testVectorRangeQueryWithEpsilon() {
    // Test without epsilon (default behavior)
    VectorRangeQuery queryDefault =
        VectorRangeQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .distanceThreshold(0.5f)
            .numResults(10)
            .build();

    List<Map<String, Object>> resultsDefault = hnswIndex.query(queryDefault);

    // Test with epsilon=0.05 (allows 5% broader search radius)
    VectorRangeQuery queryWithEpsilon =
        VectorRangeQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .distanceThreshold(0.5f)
            .epsilon(0.05f) // Broader candidate exploration
            .numResults(10)
            .build();

    List<Map<String, Object>> resultsWithEpsilon = hnswIndex.query(queryWithEpsilon);

    // With epsilon, we might get more or same results (never fewer)
    assertThat(resultsWithEpsilon.size()).isGreaterThanOrEqualTo(resultsDefault.size());
  }

  @Test
  @DisplayName("VectorRangeQuery: Should validate epsilon must be non-negative")
  void testVectorRangeQueryEpsilonValidation() {
    assertThatThrownBy(
            () ->
                VectorRangeQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .distanceThreshold(0.5f)
                    .epsilon(-0.1f) // Invalid: must be non-negative
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("epsilon must be non-negative");
  }

  @Test
  @DisplayName("VectorRangeQuery: Should support all SVS-VAMANA parameters")
  void testVectorRangeQueryWithAllSVSParameters() {
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .vector(VECTOR1)
            .field("embedding")
            .distanceThreshold(0.5f)
            .epsilon(0.05f)
            .searchWindowSize(40)
            .useSearchHistory("ON")
            .searchBufferCapacity(100)
            .numResults(10)
            .build();

    List<Map<String, Object>> results = hnswIndex.query(query);
    assertThat(results).isNotEmpty();
  }

  @Test
  @DisplayName("VectorRangeQuery: Should validate all SVS-VAMANA parameters")
  void testVectorRangeQuerySVSParameterValidation() {
    // Test searchWindowSize validation
    assertThatThrownBy(
            () ->
                VectorRangeQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .distanceThreshold(0.5f)
                    .searchWindowSize(0)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchWindowSize must be positive");

    // Test useSearchHistory validation
    assertThatThrownBy(
            () ->
                VectorRangeQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .distanceThreshold(0.5f)
                    .useSearchHistory("INVALID")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("useSearchHistory must be one of: OFF, ON, AUTO");

    // Test searchBufferCapacity validation
    assertThatThrownBy(
            () ->
                VectorRangeQuery.builder()
                    .vector(VECTOR1)
                    .field("embedding")
                    .distanceThreshold(0.5f)
                    .searchBufferCapacity(0)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("searchBufferCapacity must be positive");
  }
}
