package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for AggregateHybridQuery - the FT.AGGREGATE-based hybrid search.
 *
 * <p>Ported from Python test_aggregation.py
 *
 * <p>AggregateHybridQuery combines text and vector search using Redis aggregation to score
 * documents based on a weighted combination of text and vector similarity.
 */
@Tag("integration")
@DisplayName("AggregateHybridQuery Integration Tests")
class AggregateHybridQueryIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;

  @BeforeEach
  void setUp() {
    String schemaYaml =
        """
            version: '0.1.0'
            index:
              name: user-index-agg-hybrid
              prefix: user-agg-hybrid
              storage_type: hash
            fields:
              - name: user
                type: tag
              - name: credit_score
                type: tag
              - name: job
                type: text
              - name: description
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
                "description",
                "A talented engineer specializing in software development",
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
                "description",
                "A medical professional with expertise in lung cancer",
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
                "description",
                "A cardiologist with 30 years of experience in heart surgery",
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
                "description",
                "An aerospace engineer working on satellite systems",
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
                "description",
                "A dermatologist focusing on skin cancer research",
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
                "description",
                "Chief executive officer of a tech startup",
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
                "description",
                "A dentist specializing in cosmetic procedures",
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

  @Test
  void testBasicAggregateHybridQuery() {
    String text = "a medical professional with expertise in lung cancer";
    String textField = "description";
    float[] vector = new float[] {0.1f, 0.1f, 0.5f};
    String vectorField = "user_embedding";
    List<String> returnFields =
        List.of("user", "credit_score", "age", "job", "location", "description");

    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textField)
            .vector(vector)
            .vectorFieldName(vectorField)
            .returnFields(returnFields)
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertThat(results).hasSize(7);
    for (Map<String, Object> doc : results) {
      assertThat(doc.get("user")).isIn("john", "derrick", "nancy", "tyler", "tim", "taimur", "joe");
      assertThat(doc).containsKeys("age", "job", "credit_score");
    }

    // Test with limited results
    AggregateHybridQuery limitedQuery =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textField)
            .vector(vector)
            .vectorFieldName(vectorField)
            .numResults(3)
            .build();

    List<Map<String, Object>> limitedResults = index.query(limitedQuery);
    assertThat(limitedResults).hasSize(3);

    double firstScore = getDoubleValue(limitedResults.get(0), "hybrid_score");
    double secondScore = getDoubleValue(limitedResults.get(1), "hybrid_score");
    double thirdScore = getDoubleValue(limitedResults.get(2), "hybrid_score");

    assertThat(firstScore).isGreaterThanOrEqualTo(secondScore);
    assertThat(secondScore).isGreaterThanOrEqualTo(thirdScore);
  }

  @Test
  void testAggregateHybridQueryEmptyTextValidation() {
    String textField = "description";
    float[] vector = new float[] {0.1f, 0.1f, 0.5f};
    String vectorField = "user_embedding";

    assertThrows(
        IllegalArgumentException.class,
        () ->
            AggregateHybridQuery.builder()
                .text("")
                .textFieldName(textField)
                .vector(vector)
                .vectorFieldName(vectorField)
                .build());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            AggregateHybridQuery.builder()
                .text("with a for but and")
                .textFieldName(textField)
                .vector(vector)
                .vectorFieldName(vectorField)
                .build());
  }

  @Test
  void testAggregateHybridQueryWithTagAndNumericFilter() {
    String text = "a medical professional with expertise in lung cancer";
    String textField = "description";
    float[] vector = new float[] {0.1f, 0.1f, 0.5f};
    String vectorField = "user_embedding";
    List<String> returnFields =
        List.of("user", "credit_score", "age", "job", "location", "description");

    Filter filterExpression =
        Filter.and(Filter.tag("credit_score", "high"), Filter.numeric("age").gt(30));

    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textField)
            .vector(vector)
            .vectorFieldName(vectorField)
            .filterExpression(filterExpression)
            .returnFields(returnFields)
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertThat(results).hasSize(2);

    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
      int age = getIntValue(result, "age");
      assertThat(age).isGreaterThan(30);
    }
  }

  @Test
  void testAggregateHybridQueryAlphaParameter() {
    String text = "a medical professional with expertise in lung cancer";
    String textField = "description";
    float[] vector = new float[] {0.1f, 0.1f, 0.5f};
    String vectorField = "user_embedding";

    for (float alpha : new float[] {0.1f, 0.5f, 0.9f}) {
      AggregateHybridQuery query =
          AggregateHybridQuery.builder()
              .text(text)
              .textFieldName(textField)
              .vector(vector)
              .vectorFieldName(vectorField)
              .alpha(alpha)
              .build();

      List<Map<String, Object>> results = index.query(query);
      assertThat(results).hasSize(7);

      for (Map<String, Object> result : results) {
        double vectorSimilarity = getDoubleValue(result, "vector_similarity");
        double textScore = getDoubleValue(result, "text_score");
        double hybridScore = getDoubleValue(result, "hybrid_score");

        double expectedScore = alpha * vectorSimilarity + (1 - alpha) * textScore;
        assertThat(Math.abs(hybridScore - expectedScore)).isLessThan(0.0001);
      }
    }
  }

  @Test
  void testAggregateHybridQueryWithStringFilterExpression() {
    String text = "a medical professional with expertise in lung cancer";
    String textField = "description";
    float[] vector = new float[] {0.1f, 0.1f, 0.5f};
    String vectorField = "user_embedding";

    String stringFilter = "(@credit_score:{high} @age:[31 +inf])";

    AggregateHybridQuery query =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textField)
            .vector(vector)
            .vectorFieldName(vectorField)
            .filterExpression(stringFilter)
            .returnFields(List.of("user", "credit_score", "age", "job"))
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertThat(results).hasSize(2);

    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
      int age = getIntValue(result, "age");
      assertThat(age).isGreaterThan(30);
    }
  }

  private double getDoubleValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return Double.parseDouble(value.toString());
  }

  private int getIntValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return Integer.parseInt(value.toString());
  }
}
