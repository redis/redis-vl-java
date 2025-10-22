package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TextQuery with field weights functionality.
 *
 * <p>Python port: tests/unit/test_text_query_weights.py
 */
@DisplayName("TextQuery Field Weights Tests")
class TextQueryWeightsTest {

  @Test
  @DisplayName("Should accept weights dictionary")
  void testTextQueryAcceptsWeightsDict() {
    // Given
    String text = "example search query";
    Map<String, Double> fieldWeights = Map.of("title", 5.0, "content", 2.0, "tags", 1.0);

    // When
    TextQuery textQuery =
        TextQuery.builder().text(text).textFieldWeights(fieldWeights).numResults(10).build();

    // Then
    assertThat(textQuery.getFieldWeights()).isEqualTo(fieldWeights);
  }

  @Test
  @DisplayName("Should generate weighted query string")
  void testTextQueryGeneratesWeightedQueryString() {
    // Given
    String text = "search query";
    Map<String, Double> fieldWeights = Map.of("title", 5.0);

    // When
    TextQuery textQuery =
        TextQuery.builder().text(text).textFieldWeights(fieldWeights).numResults(10).build();

    String queryString = textQuery.toQueryString();

    // Then - should generate: @title:(search | query) => { $weight: 5.0 }
    assertThat(queryString)
        .containsAnyOf(
            "@title:(search | query) => { $weight: 5.0 }",
            "@title:(search | query)=>{ $weight: 5.0 }",
            "@title:(search | query)=>{$weight:5.0}");
  }

  @Test
  @DisplayName("Should handle multiple fields with weights")
  void testTextQueryMultipleFieldsWithWeights() {
    // Given
    String text = "search terms";
    Map<String, Double> fieldWeights = Map.of("title", 3.0, "content", 1.5, "tags", 1.0);

    // When
    TextQuery textQuery =
        TextQuery.builder().text(text).textFieldWeights(fieldWeights).numResults(10).build();

    String queryString = textQuery.toQueryString();

    // Then - all fields should be present
    assertThat(queryString).contains("@title:");
    assertThat(queryString).contains("@content:");
    assertThat(queryString).contains("@tags:");

    // Weights should be in the query
    assertThat(queryString).containsAnyOf("$weight: 3.0", "$weight:3.0");
    assertThat(queryString).containsAnyOf("$weight: 1.5", "$weight:1.5");
    // Weight of 1.0 might be omitted as it's the default
  }

  @Test
  @DisplayName("Should maintain backward compatibility with single string field")
  void testTextQueryBackwardCompatibility() {
    // Given
    String text = "backward compatible";

    // When - use single string field name (original API)
    TextQuery textQuery =
        TextQuery.builder().text(text).textField("description").numResults(5).build();

    String queryString = textQuery.toQueryString();

    // Then
    assertThat(queryString).contains("@description:");
    assertThat(queryString).contains("backward | compatible");

    // Field weights should have the single field with weight 1.0
    assertThat(textQuery.getFieldWeights()).isEqualTo(Map.of("description", 1.0));
  }

  @Test
  @DisplayName("Should reject negative weights")
  void testTextQueryRejectsNegativeWeights() {
    // Given
    String text = "test query";

    // When/Then - negative weight should throw
    assertThatThrownBy(
            () ->
                TextQuery.builder()
                    .text(text)
                    .textFieldWeights(Map.of("title", -1.0))
                    .numResults(10)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  @DisplayName("Should reject zero weights")
  void testTextQueryRejectsZeroWeights() {
    // Given
    String text = "test query";

    // When/Then - zero weight should throw
    assertThatThrownBy(
            () ->
                TextQuery.builder()
                    .text(text)
                    .textFieldWeights(Map.of("title", 0.0))
                    .numResults(10)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  @DisplayName("Should support dynamic weight updates")
  void testSetFieldWeightsMethod() {
    // Given
    String text = "dynamic weights";

    // When - start with single field
    TextQuery textQuery = TextQuery.builder().text(text).textField("title").numResults(10).build();

    assertThat(textQuery.getFieldWeights()).isEqualTo(Map.of("title", 1.0));

    // Update to multiple fields with weights
    Map<String, Double> newWeights = Map.of("title", 5.0, "content", 2.0);
    textQuery.setFieldWeights(newWeights);

    // Then
    assertThat(textQuery.getFieldWeights()).isEqualTo(newWeights);

    // Query string should reflect new weights
    String queryString = textQuery.toQueryString();
    assertThat(queryString).containsAnyOf("$weight: 5.0", "$weight:5.0");
    assertThat(queryString).containsAnyOf("$weight: 2.0", "$weight:2.0");
  }
}
