package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.schema.VectorField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for VectorQuery */
@DisplayName("VectorQuery Tests")
class VectorQueryTest {

  @Test
  @DisplayName("Should create VectorQuery with float array")
  void shouldCreateVectorQueryWithFloatArray() {
    // Given
    float[] vector = new float[] {0.1f, 0.2f, 0.3f};
    String fieldName = "embedding";
    int k = 5;

    // When
    VectorQuery query = VectorQuery.builder().field(fieldName).vector(vector).numResults(k).build();

    // Then
    assertThat(query.getField()).isEqualTo(fieldName);
    assertThat(query.getVector()).isEqualTo(vector);
    assertThat(query.getNumResults()).isEqualTo(k);
    assertThat(query.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.COSINE);
  }

  @Test
  @DisplayName("Should create VectorQuery with double array")
  void shouldCreateVectorQueryWithDoubleArray() {
    // Given
    double[] vector = new double[] {0.1, 0.2, 0.3};
    String fieldName = "embedding";

    // When
    VectorQuery query =
        VectorQuery.of(fieldName, vector)
            .numResults(10)
            .withDistanceMetric(VectorField.DistanceMetric.L2)
            .build();

    // Then
    assertThat(query.getField()).isEqualTo(fieldName);
    assertThat(query.getNumResults()).isEqualTo(10);
    assertThat(query.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.L2);
  }

  @Test
  @DisplayName("Should use default values")
  void shouldUseDefaultValues() {
    // Given
    float[] vector = new float[] {0.1f, 0.2f};

    // When
    VectorQuery query = VectorQuery.builder().field("vec").vector(vector).build();

    // Then
    assertThat(query.getNumResults()).isEqualTo(10); // Default k
    assertThat(query.getDistanceMetric())
        .isEqualTo(VectorField.DistanceMetric.COSINE); // Default metric
    assertThat(query.isReturnDistance()).isTrue(); // Default return distance
    assertThat(query.isReturnScore()).isFalse(); // Default return score
  }

  @Test
  @DisplayName("Should set return options")
  void shouldSetReturnOptions() {
    // When
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f})
            .returnDistance(false)
            .returnScore(true)
            .build();

    // Then
    assertThat(query.isReturnDistance()).isFalse();
    assertThat(query.isReturnScore()).isTrue();
  }

  @Test
  @DisplayName("Should validate required fields")
  void shouldValidateRequiredFields() {
    // Missing field name
    assertThatThrownBy(() -> VectorQuery.builder().vector(new float[] {0.1f}).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name is required");

    // Missing vector
    assertThatThrownBy(() -> VectorQuery.builder().field("embedding").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Vector is required");

    // Empty vector
    assertThatThrownBy(() -> VectorQuery.builder().field("embedding").vector(new float[0]).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Vector cannot be empty");
  }

  @Test
  @DisplayName("Should validate k value")
  void shouldValidateKValue() {
    // Zero k
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .field("embedding")
                    .vector(new float[] {0.1f})
                    .numResults(0)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numResults must be positive");

    // Negative k
    assertThatThrownBy(
            () ->
                VectorQuery.builder()
                    .field("embedding")
                    .vector(new float[] {0.1f})
                    .numResults(-1)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numResults must be positive");
  }

  @Test
  @DisplayName("Should build query string for KNN search")
  void shouldBuildQueryStringForKnnSearch() {
    // Given
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .numResults(5)
            .build();

    // When
    String queryString = query.toQueryString();

    // Then
    assertThat(queryString).contains("*=>[KNN $K @embedding $vec AS vector_distance]");
  }

  @Test
  @DisplayName("Should add pre-filter to query")
  void shouldAddPreFilterToQuery() {
    // Given
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f})
            .numResults(5)
            .preFilter("@category:{electronics}")
            .build();

    // When
    String queryString = query.toQueryString();

    // Then
    assertThat(queryString).contains("@category:{electronics}");
    assertThat(queryString).contains("=>[KNN $K @embedding $vec AS vector_distance]");
  }

  @Test
  @DisplayName("Should support hybrid search with text")
  void shouldSupportHybridSearchWithText() {
    // Given
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f})
            .numResults(5)
            .hybridField("title")
            .hybridQuery("redis database")
            .build();

    // Then
    assertThat(query.getHybridField()).isEqualTo("title");
    assertThat(query.getHybridQuery()).isEqualTo("redis database");
  }

  @Test
  @DisplayName("Should set EF runtime parameter for HNSW")
  void shouldSetEfRuntimeParameterForHnsw() {
    // When
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f})
            .numResults(5)
            .efRuntime(100)
            .build();

    // Then
    assertThat(query.getEfRuntime()).isEqualTo(100);
  }

  @Test
  @DisplayName("Should convert to parameter map")
  void shouldConvertToParameterMap() {
    // Given
    float[] vector = new float[] {0.1f, 0.2f};
    VectorQuery query =
        VectorQuery.builder().field("embedding").vector(vector).numResults(3).build();

    // When
    var params = query.toParams();

    // Then
    assertThat(params).containsKey("K");
    assertThat(params.get("K")).isEqualTo(3);
    assertThat(params).containsKey("vec");
    assertThat(params.get("vec")).isInstanceOf(byte[].class);
  }

  @Test
  @DisplayName("Should create copy with modified k")
  void shouldCreateCopyWithModifiedK() {
    // Given
    VectorQuery original =
        VectorQuery.builder().field("embedding").vector(new float[] {0.1f}).numResults(5).build();

    // When
    VectorQuery modified = original.withNumResults(10);

    // Then
    assertThat(modified).isNotSameAs(original);
    assertThat(modified.getNumResults()).isEqualTo(10);
    assertThat(original.getNumResults()).isEqualTo(5); // Original unchanged
  }
}
