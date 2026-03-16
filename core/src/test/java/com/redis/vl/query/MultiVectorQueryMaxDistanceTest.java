package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for configurable max_distance in MultiVectorQuery and Vector.
 *
 * <p>Ported from Python: commit 61f0f41 - Expose max_distance as an optional setting in multi
 * vector queries
 *
 * <p>Python reference: tests/unit/test_aggregation_types.py
 */
@DisplayName("MultiVectorQuery max_distance Tests")
class MultiVectorQueryMaxDistanceTest {

  private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f};
  private static final float[] SAMPLE_VECTOR_2 = {0.4f, 0.5f};

  @Test
  @DisplayName("Vector: Should default max_distance to 2.0")
  void testVectorDefaultMaxDistance() {
    Vector vector = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").build();

    assertThat(vector.getMaxDistance()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Vector: Should accept custom max_distance")
  void testVectorCustomMaxDistance() {
    Vector vector =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").maxDistance(0.5).build();

    assertThat(vector.getMaxDistance()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("Vector: Should reject max_distance below 0.0")
  void testVectorMaxDistanceBelowZero() {
    assertThatThrownBy(
            () ->
                Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").maxDistance(-0.1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max_distance must be a value between 0.0 and 2.0");
  }

  @Test
  @DisplayName("Vector: Should reject max_distance above 2.0")
  void testVectorMaxDistanceAboveTwo() {
    assertThatThrownBy(
            () ->
                Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").maxDistance(2.1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max_distance must be a value between 0.0 and 2.0");
  }

  @Test
  @DisplayName("Vector: Should accept max_distance at boundary 0.0")
  void testVectorMaxDistanceAtZero() {
    Vector vector =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").maxDistance(0.0).build();

    assertThat(vector.getMaxDistance()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Vector: Should accept max_distance at boundary 2.0")
  void testVectorMaxDistanceAtTwo() {
    Vector vector =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").maxDistance(2.0).build();

    assertThat(vector.getMaxDistance()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("MultiVectorQuery: Should use per-vector max_distance in query string")
  void testQueryStringUsesPerVectorMaxDistance() {
    Vector vector1 =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").maxDistance(0.5).build();

    Vector vector2 =
        Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").maxDistance(1.0).build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String queryString = query.toQueryString();

    // Each vector should use its own max_distance
    assertThat(queryString).contains("@field_1:[VECTOR_RANGE 0.5 $vector_0]");
    assertThat(queryString).contains("@field_2:[VECTOR_RANGE 1.0 $vector_1]");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should default to 2.0 when max_distance not set")
  void testQueryStringDefaultMaxDistance() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vector(vector1).build();

    String queryString = query.toQueryString();

    assertThat(queryString).contains("VECTOR_RANGE 2.0");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should use AND join instead of pipe")
  void testQueryStringUsesAndJoin() {
    Vector vector1 =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").maxDistance(0.5).build();

    Vector vector2 =
        Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").maxDistance(1.0).build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String queryString = query.toQueryString();

    // Python changed from " | " to " AND " in commit 61f0f41
    assertThat(queryString).doesNotContain(" | ");
    assertThat(queryString).contains(" AND ");
  }

  @Test
  @DisplayName("MultiVectorQuery: Query string with mixed default and custom max_distance")
  void testQueryStringMixedMaxDistance() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build(); // default

    Vector vector2 =
        Vector.builder()
            .vector(SAMPLE_VECTOR_2)
            .fieldName("field_2")
            .maxDistance(0.3)
            .build(); // custom

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String queryString = query.toQueryString();

    assertThat(queryString).contains("@field_1:[VECTOR_RANGE 2.0 $vector_0]");
    assertThat(queryString).contains("@field_2:[VECTOR_RANGE 0.3 $vector_1]");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should preserve full precision in query string")
  void testQueryStringPreservesFullPrecision() {
    Vector vector1 =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").maxDistance(0.01).build();

    Vector vector2 =
        Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").maxDistance(0.05).build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String queryString = query.toQueryString();

    // These would have been truncated to 0.0 and 0.1 with %.1f format
    assertThat(queryString).contains("VECTOR_RANGE 0.01");
    assertThat(queryString).contains("VECTOR_RANGE 0.05");
  }
}
