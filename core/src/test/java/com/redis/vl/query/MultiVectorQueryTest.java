package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Vector and MultiVectorQuery classes (issue #402).
 *
 * <p>Ported from Python: tests/unit/test_aggregation_types.py
 *
 * <p>Python reference: PR #402 - Multi-vector query support
 */
@DisplayName("Multi-Vector Query Tests")
class MultiVectorQueryTest {

  private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f};
  private static final float[] SAMPLE_VECTOR_2 = {0.4f, 0.5f};
  private static final float[] SAMPLE_VECTOR_3 = {0.6f, 0.7f, 0.8f, 0.9f};
  private static final float[] SAMPLE_VECTOR_4 = {0.2f, 0.3f};

  // ========== Vector Class Tests ==========

  @Test
  @DisplayName("Vector: Should create with required fields")
  void testVectorCreation() {
    Vector vector = Vector.builder().vector(SAMPLE_VECTOR).fieldName("text_embedding").build();

    assertThat(vector.getVector()).isEqualTo(SAMPLE_VECTOR);
    assertThat(vector.getFieldName()).isEqualTo("text_embedding");
    assertThat(vector.getDtype()).isEqualTo("float32"); // Default
    assertThat(vector.getWeight()).isEqualTo(1.0); // Default
  }

  @Test
  @DisplayName("Vector: Should create with all fields")
  void testVectorCreationAllFields() {
    Vector vector =
        Vector.builder()
            .vector(SAMPLE_VECTOR)
            .fieldName("text_embedding")
            .dtype("float64")
            .weight(0.7)
            .build();

    assertThat(vector.getVector()).isEqualTo(SAMPLE_VECTOR);
    assertThat(vector.getFieldName()).isEqualTo("text_embedding");
    assertThat(vector.getDtype()).isEqualTo("float64");
    assertThat(vector.getWeight()).isEqualTo(0.7);
  }

  @Test
  @DisplayName("Vector: Should validate dtype")
  void testVectorDtypeValidation() {
    // Valid dtypes (case-insensitive)
    assertThatCode(
            () ->
                Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").dtype("float32").build())
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").dtype("FLOAT64").build())
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").dtype("bfloat16").build())
        .doesNotThrowAnyException();

    // Invalid dtype
    assertThatThrownBy(
            () -> Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").dtype("float").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid data type");
  }

  @Test
  @DisplayName("Vector: Should require non-null vector")
  void testVectorRequiresVector() {
    assertThatThrownBy(() -> Vector.builder().fieldName("field").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Vector cannot be null or empty");
  }

  @Test
  @DisplayName("Vector: Should require non-empty vector")
  void testVectorRequiresNonEmptyVector() {
    assertThatThrownBy(() -> Vector.builder().vector(new float[] {}).fieldName("field").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Vector cannot be null or empty");
  }

  @Test
  @DisplayName("Vector: Should require non-null field name")
  void testVectorRequiresFieldName() {
    assertThatThrownBy(() -> Vector.builder().vector(SAMPLE_VECTOR).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");
  }

  @Test
  @DisplayName("Vector: Should require positive weight")
  void testVectorRequiresPositiveWeight() {
    assertThatThrownBy(
            () -> Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").weight(0.0).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Weight must be positive");

    assertThatThrownBy(
            () -> Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").weight(-0.5).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Weight must be positive");
  }

  @Test
  @DisplayName("Vector: Should be immutable (defensive copy)")
  void testVectorImmutability() {
    float[] original = SAMPLE_VECTOR.clone();
    Vector vector = Vector.builder().vector(original).fieldName("field").build();

    // Modify original
    original[0] = 999.0f;

    // Vector should not be affected
    assertThat(vector.getVector()[0]).isEqualTo(SAMPLE_VECTOR[0]);
  }

  // ========== MultiVectorQuery Class Tests ==========

  @Test
  @DisplayName("MultiVectorQuery: Should require at least one vector")
  void testMultiVectorQueryRequiresVectors() {
    assertThatThrownBy(() -> MultiVectorQuery.builder().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one Vector is required");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should reject null vectors in list")
  void testMultiVectorQueryRejectsNullInList() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field1").build();

    assertThatThrownBy(
            () -> MultiVectorQuery.builder().vectors(Arrays.asList(vector1, null)).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot contain null values");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should accept single vector")
  void testMultiVectorQuerySingleVector() {
    Vector vector = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vector(vector).build();

    assertThat(query.getVectors()).hasSize(1);
    assertThat(query.getVectors().get(0)).isEqualTo(vector);
    assertThat(query.getNumResults()).isEqualTo(10); // Default
    assertThat(query.getDialect()).isEqualTo(2); // Default
    assertThat(query.getFilterExpression()).isNull();
    assertThat(query.getReturnFields()).isEmpty();
  }

  @Test
  @DisplayName("MultiVectorQuery: Should accept multiple vectors")
  void testMultiVectorQueryMultipleVectors() {
    Vector vector1 =
        Vector.builder()
            .vector(SAMPLE_VECTOR)
            .fieldName("field_1")
            .weight(0.2)
            .dtype("float32")
            .build();

    Vector vector2 =
        Vector.builder()
            .vector(SAMPLE_VECTOR_2)
            .fieldName("field_2")
            .weight(0.5)
            .dtype("float32")
            .build();

    Vector vector3 =
        Vector.builder()
            .vector(SAMPLE_VECTOR_3)
            .fieldName("field_3")
            .weight(0.6)
            .dtype("float32")
            .build();

    Vector vector4 =
        Vector.builder()
            .vector(SAMPLE_VECTOR_4)
            .fieldName("field_4")
            .weight(0.1)
            .dtype("float32")
            .build();

    List<Vector> vectors = Arrays.asList(vector1, vector2, vector3, vector4);
    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vectors).build();

    assertThat(query.getVectors()).hasSize(4);
    assertThat(query.getVectors()).isEqualTo(vectors);
  }

  @Test
  @DisplayName("MultiVectorQuery: Should override defaults")
  void testMultiVectorQueryOverrideDefaults() {
    Vector vector = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    Filter filter = Filter.tag("user_group", "group_A", "group_C");

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vector(vector)
            .filterExpression(filter)
            .numResults(5)
            .returnFields("field_1", "user_name", "address")
            .dialect(3)
            .build();

    assertThat(query.getFilterExpression()).isEqualTo(filter);
    assertThat(query.getNumResults()).isEqualTo(5);
    assertThat(query.getReturnFields()).containsExactly("field_1", "user_name", "address");
    assertThat(query.getDialect()).isEqualTo(3);
  }

  @Test
  @DisplayName("MultiVectorQuery: Should build correct query string")
  void testMultiVectorQueryString() {
    // Python test: test_multi_vector_query_string
    String field1 = "text embedding";
    String field2 = "image embedding";
    double weight1 = 0.2;
    double weight2 = 0.7;

    Vector vector1 =
        Vector.builder().vector(SAMPLE_VECTOR_2).fieldName(field1).weight(weight1).build();

    Vector vector2 =
        Vector.builder().vector(SAMPLE_VECTOR_3).fieldName(field2).weight(weight2).build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String queryString = query.toQueryString();

    // Expected format:
    // @field1:[VECTOR_RANGE 2.0 $vector_0]=>{$YIELD_DISTANCE_AS: distance_0} |
    // @field2:[VECTOR_RANGE 2.0 $vector_1]=>{$YIELD_DISTANCE_AS: distance_1}
    assertThat(queryString)
        .contains(String.format("@%s:[VECTOR_RANGE 2.0 $vector_0]", field1))
        .contains("{$YIELD_DISTANCE_AS: distance_0}")
        .contains(String.format("@%s:[VECTOR_RANGE 2.0 $vector_1]", field2))
        .contains("{$YIELD_DISTANCE_AS: distance_1}")
        .contains(" | ");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should build params map")
  void testMultiVectorQueryParams() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    Vector vector2 = Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    Map<String, Object> params = query.toParams();

    assertThat(params).containsKeys("vector_0", "vector_1");
    assertThat(params.get("vector_0")).isInstanceOf(byte[].class);
    assertThat(params.get("vector_1")).isInstanceOf(byte[].class);
  }

  @Test
  @DisplayName("MultiVectorQuery: Should generate scoring formula")
  void testMultiVectorQueryScoringFormula() {
    Vector vector1 =
        Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").weight(0.2).build();

    Vector vector2 =
        Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").weight(0.7).build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    String formula = query.getScoringFormula();

    // Expected: "0.20 * score_0 + 0.70 * score_1"
    assertThat(formula).contains("0.20 * score_0").contains("0.70 * score_1").contains(" + ");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should generate score calculations")
  void testMultiVectorQueryScoreCalculations() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    Vector vector2 = Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    Map<String, String> calculations = query.getScoreCalculations();

    assertThat(calculations).hasSize(2);
    assertThat(calculations.get("score_0")).isEqualTo("(2 - distance_0)/2");
    assertThat(calculations.get("score_1")).isEqualTo("(2 - distance_1)/2");
  }

  @Test
  @DisplayName("MultiVectorQuery: Should include filter in query string")
  void testMultiVectorQueryWithFilter() {
    Vector vector = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field").build();

    Filter filter = Filter.tag("category", "electronics");

    MultiVectorQuery query =
        MultiVectorQuery.builder().vector(vector).filterExpression(filter).build();

    String queryString = query.toQueryString();

    // Should wrap base query and filter with AND
    assertThat(queryString).contains(" AND ");
    assertThat(queryString).contains(filter.build());
  }

  @Test
  @DisplayName("MultiVectorQuery: Should support varargs vectors")
  void testMultiVectorQueryVarargs() {
    Vector vector1 = Vector.builder().vector(SAMPLE_VECTOR).fieldName("field_1").build();

    Vector vector2 = Vector.builder().vector(SAMPLE_VECTOR_2).fieldName("field_2").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(vector1, vector2).build();

    assertThat(query.getVectors()).hasSize(2);
  }

  @Test
  @DisplayName("Vector: Should implement equals and hashCode")
  void testVectorEqualsAndHashCode() {
    Vector vector1 =
        Vector.builder()
            .vector(SAMPLE_VECTOR)
            .fieldName("field")
            .dtype("float32")
            .weight(0.7)
            .build();

    Vector vector2 =
        Vector.builder()
            .vector(SAMPLE_VECTOR)
            .fieldName("field")
            .dtype("float32")
            .weight(0.7)
            .build();

    Vector vector3 =
        Vector.builder()
            .vector(SAMPLE_VECTOR_2)
            .fieldName("field")
            .dtype("float32")
            .weight(0.7)
            .build();

    assertThat(vector1).isEqualTo(vector2);
    assertThat(vector1.hashCode()).isEqualTo(vector2.hashCode());
    assertThat(vector1).isNotEqualTo(vector3);
  }
}
