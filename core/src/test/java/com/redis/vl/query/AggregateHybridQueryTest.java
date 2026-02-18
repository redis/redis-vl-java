package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AggregateHybridQuery - ported from Python test_aggregation_types.py
 *
 * <p>Python reference: /redis-vl-python/tests/unit/test_aggregation_types.py
 *
 * <p>Tests the FT.AGGREGATE-based hybrid search (AggregateHybridQuery).
 */
@DisplayName("AggregateHybridQuery Unit Tests")
class AggregateHybridQueryTest {

  private static final float[] SAMPLE_VECTOR = new float[] {0.1f, 0.2f, 0.3f};

  @Test
  @DisplayName("Should support string filter expressions")
  void testAggregateHybridQueryWithStringFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    String stringFilter = "@category:{tech|science|engineering}";
    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression(stringFilter)
            .build();

    assertThat(query.getFilterExpression()).isEqualTo(stringFilter);

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).contains("AND " + stringFilter);
  }

  @Test
  @DisplayName("Should support Filter objects")
  void testAggregateHybridQueryWithFilterObject() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    Filter filterExpression = Filter.tag("category", "tech");
    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression(filterExpression)
            .build();

    assertThat(query.getFilterExpression()).isEqualTo(filterExpression);

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).contains("AND @category:{tech}");
  }

  @Test
  @DisplayName("Should work without filter")
  void testAggregateHybridQueryNoFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .build();

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).doesNotContain("AND");
  }

  @Test
  @DisplayName("Should handle wildcard filter")
  void testAggregateHybridQueryWildcardFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression("*")
            .build();

    String queryString = query.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).doesNotContain("AND");
  }

  @Test
  @DisplayName("Should reject empty text")
  void testRejectsEmptyText() {
    assertThatThrownBy(
            () ->
                AggregateHybridQuery.builder()
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
                AggregateHybridQuery.builder()
                    .text("with a for but and")
                    .textFieldName("description")
                    .vector(SAMPLE_VECTOR)
                    .vectorFieldName("embedding")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text string cannot be empty after removing stopwords");
  }

  @Test
  @DisplayName("Should build correct query string format")
  void testQueryStringFormat() {
    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("user_embedding")
            .numResults(5)
            .build();

    String queryString = query.buildQueryString();

    assertThat(queryString).matches(".*\\(~@description:\\(.*\\)\\)=>\\[KNN.*\\]");
    assertThat(queryString).contains("KNN 5 @user_embedding");
    assertThat(queryString).contains("AS vector_distance");
  }

  @Test
  @DisplayName("Should store alpha parameter correctly")
  void testAlphaParameter() {
    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text("test")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .alpha(0.3f)
            .build();

    assertThat(query.getAlpha()).isEqualTo(0.3f);
  }

  @Test
  @DisplayName("Should store numResults parameter correctly")
  void testNumResultsParameter() {
    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text("test")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .numResults(20)
            .build();

    assertThat(query.getNumResults()).isEqualTo(20);
  }
}
