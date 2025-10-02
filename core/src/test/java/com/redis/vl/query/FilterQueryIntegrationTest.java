package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for FilterQuery - ported from Python test_query.py
 *
 * <p>Python reference: /redis-vl-python/tests/integration/test_query.py:568-617
 */
@DisplayName("FilterQuery Integration Tests")
class FilterQueryIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Create index matching Python fixture (lines 140-175)
    String schemaYaml =
        """
            version: '0.1.0'
            index:
              name: user-index-filter
              prefix: user-filter
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

    // Load test data (matching Python sample_data)
    List<Map<String, Object>> data =
        List.of(
            Map.of(
                "user", "john",
                "age", 18,
                "job", "engineer",
                "credit_score", "high",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "derrick",
                "age", 14,
                "job", "doctor",
                "credit_score", "low",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "nancy",
                "age", 94,
                "job", "doctor",
                "credit_score", "high",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "tyler",
                "age", 100,
                "job", "engineer",
                "credit_score", "high",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "tim",
                "age", 12,
                "job", "dermatologist",
                "credit_score", "high",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "taimur",
                "age", 15,
                "job", "CEO",
                "credit_score", "low",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}),
            Map.of(
                "user", "joe",
                "age", 35,
                "job", "dentist",
                "credit_score", "medium",
                "location", "-122.4194,37.7749",
                "user_embedding", new float[] {0.1f, 0.1f, 0.5f}));

    index.load(data, "user");
  }

  @AfterEach
  void tearDown() {
    if (index != null) {
      index.delete(true);
    }
  }

  /** Port of Python test_paginate_filter_query (line 568) */
  @Test
  void testPaginateFilterQuery() {
    // Python: filter_expression=Tag("credit_score") == "high"
    FilterQuery filterQuery =
        FilterQuery.builder()
            .filterExpression(Filter.tag("credit_score", "high"))
            .returnFields(List.of("user", "credit_score", "age", "job", "location", "last_updated"))
            .numResults(10)
            .build();

    int batchSize = 3;
    List<Map<String, Object>> allResults = new ArrayList<>();
    int iterations = 0;

    // Use pagination (Python: index.paginate(filter_query, batch_size))
    for (List<Map<String, Object>> batch : index.paginate(filterQuery, batchSize)) {
      allResults.addAll(batch);
      assertThat(batch).hasSizeLessThanOrEqualTo(batchSize);
      iterations++;
    }

    // Python: expected_count = 4
    int expectedCount = 4; // john, nancy, tyler, tim have "high" credit score
    int expectedIterations =
        (int) Math.ceil((double) expectedCount / batchSize); // Ceiling division

    assertThat(allResults).hasSize(expectedCount);
    assertThat(iterations).isEqualTo(expectedIterations);
    assertThat(allResults).allMatch(item -> "high".equals(item.get("credit_score")));
  }

  /** Port of Python test_sort_filter_query (line 596) */
  @Test
  void testSortFilterQuery() {
    // Python: sorted_filter_query fixture with sort_by="age"
    FilterQuery sortedQuery =
        FilterQuery.builder()
            .filterExpression(Filter.tag("credit_score", "high"))
            .returnFields(List.of("user", "credit_score", "age", "job", "location", "last_updated"))
            .sortBy("age")
            .build();

    List<Map<String, Object>> results = index.query(sortedQuery);

    // Verify results are sorted by age (ascending)
    assertThat(results).isNotEmpty();

    List<Integer> ages =
        results.stream()
            .map(
                r -> {
                  Object ageObj = r.get("age");
                  if (ageObj instanceof Number) {
                    return ((Number) ageObj).intValue();
                  } else {
                    return Integer.parseInt(ageObj.toString());
                  }
                })
            .toList();

    // Check sorted ascending (12, 18, 94, 100)
    for (int i = 0; i < ages.size() - 1; i++) {
      assertThat(ages.get(i)).isLessThanOrEqualTo(ages.get(i + 1));
    }
  }

  /**
   * Port of Python test_query_with_chunk_number_zero (line 611) Tests that numeric filter with
   * value 0 works correctly
   */
  @Test
  void testQueryWithChunkNumberZero() {
    // Python test verifies Filter expression with Num("chunk_number") == 0
    String docBaseId = "8675309";
    String fileId = "e9ffbac9ff6f67cc";
    int chunkNum = 0;

    Filter filterConditions =
        Filter.and(
            Filter.and(Filter.tag("doc_base_id", docBaseId), Filter.tag("file_id", fileId)),
            Filter.numeric("chunk_number").eq(chunkNum));

    // Python expected: "((@doc_base_id:{8675309} @file_id:{e9ffbac9ff6f67cc}) @chunk_number:[0 0])"
    String queryStr = filterConditions.build();

    assertThat(queryStr).contains("@doc_base_id:{8675309}");
    assertThat(queryStr).contains("@file_id:{e9ffbac9ff6f67cc}");
    assertThat(queryStr).contains("@chunk_number:[0 0]");
  }

  /** Additional test: FilterQuery with no filter (match all) */
  @Test
  void testFilterQueryMatchAll() {
    FilterQuery query =
        FilterQuery.builder().returnFields(List.of("user", "age")).numResults(10).build();

    List<Map<String, Object>> results = index.query(query);

    // Should return all 7 documents
    assertThat(results).hasSize(7);
  }

  /** Additional test: Complex filter combinations */
  @Test
  void testComplexFilterCombinations() {
    // Filter: credit_score == "high" AND age > 50
    Filter combined = Filter.and(Filter.tag("credit_score", "high"), Filter.numeric("age").gt(50));

    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(combined)
            .returnFields(List.of("user", "age", "credit_score"))
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Should match nancy (94) and tyler (100)
    assertThat(results).hasSize(2);
    assertThat(results)
        .allMatch(
            r -> {
              Object ageObj = r.get("age");
              int age =
                  (ageObj instanceof Number)
                      ? ((Number) ageObj).intValue()
                      : Integer.parseInt(ageObj.toString());
              return "high".equals(r.get("credit_score")) && age > 50;
            });
  }
}
