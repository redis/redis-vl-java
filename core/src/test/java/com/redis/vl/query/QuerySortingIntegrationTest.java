package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for query sorting functionality.
 *
 * <p>Ported from Python test_query.py:
 *
 * <ul>
 *   <li>test_sort_vector_query (line 601)
 *   <li>test_sort_range_query (line 606)
 * </ul>
 *
 * <p>Python reference: /redis-vl-python/tests/integration/test_query.py
 */
@DisplayName("Query Sorting Integration Tests")
class QuerySortingIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Create index matching Python fixture (test_query.py:147-185)
    String schemaYaml =
        """
            version: '0.1.0'
            index:
              name: user-index-sort
              prefix: user-sort
              storage_type: hash
            fields:
              - name: user
                type: tag
              - name: credit_score
                type: tag
              - name: job
                type: text
              - name: age
                type: numeric
              - name: last_updated
                type: numeric
              - name: location
                type: geo
              - name: user_embedding
                type: vector
                attrs:
                  dims: 3
                  distance_metric: cosine
                  algorithm: flat
                  datatype: float32
            """;

    IndexSchema schema = IndexSchema.fromYaml(schemaYaml);
    index = new SearchIndex(schema, unifiedJedis);
    index.create(true);

    // Load test data with varying ages for sorting
    List<Map<String, Object>> data =
        List.of(
            Map.of(
                "user",
                "john",
                "age",
                18,
                "job",
                "engineer",
                "credit_score",
                "high",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "derrick",
                "age",
                14,
                "job",
                "doctor",
                "credit_score",
                "low",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "nancy",
                "age",
                94,
                "job",
                "doctor",
                "credit_score",
                "high",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "tyler",
                "age",
                100,
                "job",
                "engineer",
                "credit_score",
                "high",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "tim",
                "age",
                12,
                "job",
                "dermatologist",
                "credit_score",
                "high",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "taimur",
                "age",
                15,
                "job",
                "CEO",
                "credit_score",
                "low",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user",
                "joe",
                "age",
                35,
                "job",
                "dentist",
                "credit_score",
                "medium",
                "location",
                "-122.4194,37.7749",
                "user_embedding",
                new float[] {0.1f, 0.1f, 0.5f}));

    index.load(data, "user");
  }

  @AfterEach
  void tearDown() {
    if (index != null) {
      index.delete(true);
    }
  }

  /** Port of Python test_sort_vector_query (line 601) */
  @Test
  void testSortVectorQuery() {
    // Create VectorQuery with sort_by="age"
    VectorQuery query =
        VectorQuery.builder()
            .field("user_embedding")
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .returnFields(List.of("user", "credit_score", "age", "job", "location", "last_updated"))
            .sortBy("age")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify we got results
    assertThat(results).hasSize(7);

    // Verify results are sorted by age in ascending order
    for (int i = 0; i < results.size() - 1; i++) {
      int currentAge = getIntValue(results.get(i), "age");
      int nextAge = getIntValue(results.get(i + 1), "age");
      assertThat(currentAge)
          .as("Age at position %d should be <= age at position %d", i, i + 1)
          .isLessThanOrEqualTo(nextAge);
    }

    // Verify first result is youngest (tim, age 12)
    assertThat(results.get(0).get("user")).isEqualTo("tim");
    assertThat(getIntValue(results.get(0), "age")).isEqualTo(12);

    // Verify last result is oldest (tyler, age 100)
    assertThat(results.get(6).get("user")).isEqualTo("tyler");
    assertThat(getIntValue(results.get(6), "age")).isEqualTo(100);
  }

  /** Port of Python test_sort_range_query (line 606) */
  @Test
  void testSortVectorRangeQuery() {
    // Create VectorRangeQuery with sort_by="age"
    // Note: Since all vectors are identical, use a large threshold to ensure matches
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("user_embedding")
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .distanceThreshold(1.0f) // Increase threshold to ensure matches
            .returnFields(List.of("user", "credit_score", "age", "job", "location"))
            .sortBy("age")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify we got results
    assertThat(results).hasSizeGreaterThan(0);

    // Verify results are sorted by age in ascending order
    for (int i = 0; i < results.size() - 1; i++) {
      int currentAge = getIntValue(results.get(i), "age");
      int nextAge = getIntValue(results.get(i + 1), "age");
      assertThat(currentAge)
          .as("Age at position %d should be <= age at position %d", i, i + 1)
          .isLessThanOrEqualTo(nextAge);
    }
  }

  /** Test sorting with descending order */
  @Test
  void testSortVectorQueryDescending() {
    // Test descending sort order
    VectorQuery query =
        VectorQuery.builder()
            .field("user_embedding")
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .returnFields(List.of("user", "age"))
            .sortBy("age")
            .sortDescending(true)
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify results are sorted by age in descending order
    for (int i = 0; i < results.size() - 1; i++) {
      int currentAge = getIntValue(results.get(i), "age");
      int nextAge = getIntValue(results.get(i + 1), "age");
      assertThat(currentAge)
          .as("Age at position %d should be >= age at position %d", i, i + 1)
          .isGreaterThanOrEqualTo(nextAge);
    }

    // Verify first result is oldest (tyler, age 100)
    assertThat(results.get(0).get("user")).isEqualTo("tyler");
    assertThat(getIntValue(results.get(0), "age")).isEqualTo(100);
  }

  /** Test sorting with FilterQuery (already implemented) */
  @Test
  void testSortFilterQueryAlreadyWorks() {
    // FilterQuery already has sortBy - verify it works
    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(Filter.tag("credit_score", "high"))
            .returnFields(List.of("user", "age", "credit_score"))
            .sortBy("age")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Verify results are sorted by age
    for (int i = 0; i < results.size() - 1; i++) {
      int currentAge = getIntValue(results.get(i), "age");
      int nextAge = getIntValue(results.get(i + 1), "age");
      assertThat(currentAge).isLessThanOrEqualTo(nextAge);
    }
  }

  // Helper method for type conversion (Hash storage returns strings)
  private int getIntValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return Integer.parseInt(value.toString());
  }
}
