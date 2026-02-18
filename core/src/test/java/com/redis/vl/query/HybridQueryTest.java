package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.query.HybridQuery.CombinationMethod;
import com.redis.vl.query.HybridQuery.VectorSearchMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.hybrid.FTHybridParams;

/**
 * Unit tests for HybridQuery - the native FT.HYBRID implementation.
 *
 * <p>Tests the builder defaults, parameter validation, vector search methods, combination methods,
 * filter expressions, and FTHybridParams construction.
 */
@DisplayName("HybridQuery Unit Tests (FT.HYBRID)")
class HybridQueryTest {

  private static final float[] SAMPLE_VECTOR = new float[] {0.1f, 0.2f, 0.3f};

  @Test
  @DisplayName("Should use correct builder defaults")
  void testBuilderDefaults() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .build();

    assertThat(query.getTextScorer()).isEqualTo("BM25STD");
    assertThat(query.getNumResults()).isEqualTo(10);
    assertThat(query.getLinearAlpha()).isEqualTo(0.3f);
    assertThat(query.getRrfWindow()).isEqualTo(20);
    assertThat(query.getRrfConstant()).isEqualTo(60);
    assertThat(query.getVectorSearchMethod()).isEqualTo(VectorSearchMethod.KNN);
    assertThat(query.getCombinationMethod()).isEqualTo(CombinationMethod.RRF);
    assertThat(query.getDtype()).isEqualTo("float32");
    assertThat(query.getKnnEfRuntime()).isEqualTo(10);
    assertThat(query.getRangeEpsilon()).isEqualTo(0.01f);
    assertThat(query.getVectorParamName()).isEqualTo("vector");
    assertThat(query.getYieldTextScoreAs()).isNull();
    assertThat(query.getYieldVsimScoreAs()).isNull();
    assertThat(query.getYieldCombinedScoreAs()).isNull();
    assertThat(query.getFilterExpression()).isNull();
    assertThat(query.getReturnFields()).isEmpty();
  }

  @Test
  @DisplayName("Should reject empty text")
  void testRejectsEmptyText() {
    assertThatThrownBy(
            () ->
                HybridQuery.builder()
                    .text("")
                    .textFieldName("description")
                    .vector(SAMPLE_VECTOR)
                    .vectorFieldName("embedding")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text string cannot be empty");
  }

  @Test
  @DisplayName("Should reject text that becomes empty after stopwords removal")
  void testRejectsTextThatBecomesEmptyAfterStopwords() {
    assertThatThrownBy(
            () ->
                HybridQuery.builder()
                    .text("with a for but and")
                    .textFieldName("description")
                    .vector(SAMPLE_VECTOR)
                    .vectorFieldName("embedding")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text string cannot be empty after removing stopwords");
  }

  @Test
  @DisplayName("Should configure KNN vector search method parameters")
  void testKnnVectorSearchMethod() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .vectorSearchMethod(VectorSearchMethod.KNN)
            .knnEfRuntime(50)
            .numResults(20)
            .build();

    assertThat(query.getVectorSearchMethod()).isEqualTo(VectorSearchMethod.KNN);
    assertThat(query.getKnnEfRuntime()).isEqualTo(50);
    assertThat(query.getNumResults()).isEqualTo(20);
  }

  @Test
  @DisplayName("Should configure RANGE vector search method parameters")
  void testRangeVectorSearchMethod() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .vectorSearchMethod(VectorSearchMethod.RANGE)
            .rangeRadius(0.5f)
            .rangeEpsilon(0.05f)
            .build();

    assertThat(query.getVectorSearchMethod()).isEqualTo(VectorSearchMethod.RANGE);
    assertThat(query.getRangeRadius()).isEqualTo(0.5f);
    assertThat(query.getRangeEpsilon()).isEqualTo(0.05f);
  }

  @Test
  @DisplayName("Should require radius for RANGE vector search method")
  void testRangeRequiresRadius() {
    assertThatThrownBy(
            () ->
                HybridQuery.builder()
                    .text("test query")
                    .textFieldName("description")
                    .vector(SAMPLE_VECTOR)
                    .vectorFieldName("embedding")
                    .vectorSearchMethod(VectorSearchMethod.RANGE)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rangeRadius is required when vectorSearchMethod is RANGE");
  }

  @Test
  @DisplayName("Should configure RRF combination method parameters")
  void testRrfCombinationMethod() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .combinationMethod(CombinationMethod.RRF)
            .rrfWindow(30)
            .rrfConstant(100)
            .build();

    assertThat(query.getCombinationMethod()).isEqualTo(CombinationMethod.RRF);
    assertThat(query.getRrfWindow()).isEqualTo(30);
    assertThat(query.getRrfConstant()).isEqualTo(100);
  }

  @Test
  @DisplayName("Should configure LINEAR combination method parameters")
  void testLinearCombinationMethod() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .combinationMethod(CombinationMethod.LINEAR)
            .linearAlpha(0.7f)
            .build();

    assertThat(query.getCombinationMethod()).isEqualTo(CombinationMethod.LINEAR);
    assertThat(query.getLinearAlpha()).isEqualTo(0.7f);
  }

  @Test
  @DisplayName("Should support string filter expressions")
  void testStringFilterExpression() {
    String stringFilter = "@category:{tech|science|engineering}";
    HybridQuery query =
        HybridQuery.builder()
            .text("search for document 12345")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .filterExpression(stringFilter)
            .build();

    assertThat(query.getFilterExpression()).isEqualTo(stringFilter);

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@description:(search | document | 12345)");
    assertThat(queryString).contains(stringFilter);
  }

  @Test
  @DisplayName("Should support Filter objects")
  void testFilterObjectExpression() {
    Filter filterExpression = Filter.tag("category", "tech");
    HybridQuery query =
        HybridQuery.builder()
            .text("search for document 12345")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .filterExpression(filterExpression)
            .build();

    assertThat(query.getFilterExpression()).isEqualTo(filterExpression);

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@description:(search | document | 12345)");
    assertThat(queryString).contains("@category:{tech}");
  }

  @Test
  @DisplayName("Should build FTHybridParams successfully")
  void testBuildFTHybridParams() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("user_embedding")
            .numResults(5)
            .combinationMethod(CombinationMethod.RRF)
            .rrfWindow(15)
            .rrfConstant(60)
            .build();

    FTHybridParams params = query.buildFTHybridParams();
    assertThat(params).isNotNull();
  }

  @Test
  @DisplayName("Should build FTHybridParams with LINEAR combination")
  void testBuildFTHybridParamsLinear() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("user_embedding")
            .combinationMethod(CombinationMethod.LINEAR)
            .linearAlpha(0.6f)
            .numResults(10)
            .build();

    FTHybridParams params = query.buildFTHybridParams();
    assertThat(params).isNotNull();
  }

  @Test
  @DisplayName("Should build FTHybridParams with RANGE vector method")
  void testBuildFTHybridParamsRange() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("user_embedding")
            .vectorSearchMethod(VectorSearchMethod.RANGE)
            .rangeRadius(0.5f)
            .rangeEpsilon(0.02f)
            .build();

    FTHybridParams params = query.buildFTHybridParams();
    assertThat(params).isNotNull();
  }

  @Test
  @DisplayName("Should yield score alias fields")
  void testYieldScoreAliases() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_score")
            .yieldCombinedScoreAs("combined_score")
            .build();

    assertThat(query.getYieldTextScoreAs()).isEqualTo("text_score");
    assertThat(query.getYieldVsimScoreAs()).isEqualTo("vector_score");
    assertThat(query.getYieldCombinedScoreAs()).isEqualTo("combined_score");

    FTHybridParams params = query.buildFTHybridParams();
    assertThat(params).isNotNull();
  }

  @Test
  @DisplayName("Should produce correct query string without filter")
  void testQueryStringNoFilter() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .build();

    String queryString = query.buildQueryString();
    assertThat(queryString).isEqualTo("@description:(medical | professional)");
  }

  @Test
  @DisplayName("Should produce correct query string with filter")
  void testQueryStringWithFilter() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .filterExpression("@age:[30 +inf]")
            .build();

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@description:(medical | professional)");
    assertThat(queryString).contains("@age:[30 +inf]");
  }

  @Test
  @DisplayName("Should handle wildcard filter without adding it to query")
  void testWildcardFilter() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .filterExpression("*")
            .build();

    String queryString = query.buildQueryString();
    assertThat(queryString).isEqualTo("@description:(medical | professional)");
    assertThat(queryString).doesNotContain("*");
  }

  @Test
  @DisplayName("Should configure return fields")
  void testReturnFields() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .returnFields(java.util.List.of("field1", "field2", "field3"))
            .build();

    assertThat(query.getReturnFields()).containsExactly("field1", "field2", "field3");

    FTHybridParams params = query.buildFTHybridParams();
    assertThat(params).isNotNull();
  }

  @Test
  @DisplayName("Should throw for unknown scorer")
  void testUnknownScorer() {
    assertThatThrownBy(
            () ->
                HybridQuery.builder()
                    .text("test query")
                    .textFieldName("description")
                    .vector(SAMPLE_VECTOR)
                    .vectorFieldName("embedding")
                    .textScorer("UNKNOWN_SCORER")
                    .build()
                    .buildFTHybridParams())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown scorer");
  }

  @Test
  @DisplayName("Should produce vector params in getParams()")
  void testGetParams() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .build();

    java.util.Map<String, Object> params = query.getParams();
    assertThat(params).containsKey("vector");
    assertThat(params.get("vector")).isInstanceOf(byte[].class);
  }

  @Test
  @DisplayName("Should support custom vector param name")
  void testCustomVectorParamName() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test query")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .vectorParamName("my_vec")
            .build();

    assertThat(query.getVectorParamName()).isEqualTo("my_vec");

    java.util.Map<String, Object> params = query.getParams();
    assertThat(params).containsKey("my_vec");
  }
}
