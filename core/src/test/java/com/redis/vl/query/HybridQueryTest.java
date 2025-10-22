package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HybridQuery - ported from Python test_aggregation_types.py
 *
 * <p>Python reference: /redis-vl-python/tests/unit/test_aggregation_types.py
 *
 * <p>Tests the ability to pass string filter expressions directly to HybridQuery, in addition to
 * Filter objects. This is a port of the test added in PR #375.
 */
@DisplayName("HybridQuery Unit Tests")
class HybridQueryTest {

  private static final float[] SAMPLE_VECTOR = new float[] {0.1f, 0.2f, 0.3f};

  /**
   * Port of Python test_hybrid_query_with_string_filter (test_aggregation_types.py:118-191)
   *
   * <p>This test ensures that when a string filter expression is passed to HybridQuery, it's
   * properly included in the generated query string and not set to empty. Regression test for bug
   * where string filters were being ignored in Python.
   *
   * <p>In Java, this test verifies we support BOTH Filter objects and raw string filters for
   * feature parity with Python.
   */
  @Test
  @DisplayName("Should support string filter expressions")
  void testHybridQueryWithStringFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    // Test with string filter expression - should include filter in query string
    String stringFilter = "@category:{tech|science|engineering}";
    HybridQuery hybridQuery =
        HybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression(stringFilter)
            .build();

    // Check that filter is stored correctly
    assertThat(hybridQuery.getFilterExpression()).isEqualTo(stringFilter);

    // Check that the generated query string includes both text search and filter
    String queryString = hybridQuery.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).contains("AND " + stringFilter);
  }

  /** Port of Python test - verify Filter objects still work */
  @Test
  @DisplayName("Should support Filter objects")
  void testHybridQueryWithFilterObject() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    // Test with FilterExpression - should also work (existing functionality)
    Filter filterExpression = Filter.tag("category", "tech");
    HybridQuery hybridQuery =
        HybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression(filterExpression)
            .build();

    // Check that filter is stored correctly
    assertThat(hybridQuery.getFilterExpression()).isEqualTo(filterExpression);

    // Check that the generated query string includes both text search and filter
    String queryString = hybridQuery.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).contains("AND @category:{tech}");
  }

  /** Port of Python test - verify no filter works */
  @Test
  @DisplayName("Should work without filter")
  void testHybridQueryNoFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    // Test with no filter - should only have text search
    HybridQuery hybridQuery =
        HybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .build();

    String queryString = hybridQuery.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).doesNotContain("AND");
  }

  /** Port of Python test - verify wildcard filter works */
  @Test
  @DisplayName("Should handle wildcard filter")
  void testHybridQueryWildcardFilter() {
    String text = "search for document 12345";
    String textFieldName = "description";
    String vectorFieldName = "embedding";

    // Test with wildcard filter - should only have text search (no AND clause)
    HybridQuery hybridQuery =
        HybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(SAMPLE_VECTOR)
            .vectorFieldName(vectorFieldName)
            .filterExpression("*")
            .build();

    String queryString = hybridQuery.buildQueryString();
    assertThat(queryString).contains("@" + textFieldName + ":(search | document | 12345)");
    assertThat(queryString).doesNotContain("AND");
  }

  /** Test that empty text throws exception */
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

  /** Test that text becomes empty after stopwords are removed */
  @Test
  @DisplayName("Should reject text that becomes empty after stopwords removal")
  void testRejectsTextThatBecomesEmptyAfterStopwords() {
    // "with a for but and" will all be removed as default English stopwords
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

  /** Test query string building */
  @Test
  @DisplayName("Should build correct query string format")
  void testQueryStringFormat() {
    HybridQuery query =
        HybridQuery.builder()
            .text("medical professional")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("user_embedding")
            .numResults(5)
            .build();

    String queryString = query.buildQueryString();

    // Verify format: (~@text_field:(tokens))=>[KNN num @vector_field $vector AS vector_distance]
    assertThat(queryString).matches(".*\\(~@description:\\(.*\\)\\)=>\\[KNN.*\\]");
    assertThat(queryString).contains("KNN 5 @user_embedding");
    assertThat(queryString).contains("AS vector_distance");
  }

  /** Test alpha parameter */
  @Test
  @DisplayName("Should store alpha parameter correctly")
  void testAlphaParameter() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .alpha(0.3f)
            .build();

    assertThat(query.getAlpha()).isEqualTo(0.3f);
  }

  /** Test numResults parameter */
  @Test
  @DisplayName("Should store numResults parameter correctly")
  void testNumResultsParameter() {
    HybridQuery query =
        HybridQuery.builder()
            .text("test")
            .textFieldName("description")
            .vector(SAMPLE_VECTOR)
            .vectorFieldName("embedding")
            .numResults(20)
            .build();

    assertThat(query.getNumResults()).isEqualTo(20);
  }
}
