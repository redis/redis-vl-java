package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.*;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for Multi-Vector Query support (#402).
 *
 * <p>Tests simultaneous search across multiple vector fields with weighted score combination.
 *
 * <p>Python reference: PR #402 - Multi-vector query support
 */
@Tag("integration")
@DisplayName("Multi-Vector Query Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiVectorQueryIntegrationTest extends BaseIntegrationTest {

  private static final String INDEX_NAME = "multi_vector_test_idx";
  private static SearchIndex searchIndex;

  @BeforeAll
  static void setupIndex() {
    // Clean up any existing index
    try {
      unifiedJedis.ftDropIndex(INDEX_NAME);
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }

    // Create schema with multiple vector fields
    IndexSchema schema =
        IndexSchema.builder()
            .name(INDEX_NAME)
            .prefix("product:")
            .field(TextField.builder().name("title").build())
            .field(TextField.builder().name("description").build())
            .field(TagField.builder().name("category").build())
            .field(NumericField.builder().name("price").sortable(true).build())
            // Text embeddings (3 dimensions)
            .field(
                VectorField.builder()
                    .name("text_embedding")
                    .dimensions(3)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            // Image embeddings (2 dimensions)
            .field(
                VectorField.builder()
                    .name("image_embedding")
                    .dimensions(2)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            .build();

    searchIndex = new SearchIndex(schema, unifiedJedis);
    searchIndex.create();

    // Insert test documents with multiple vector embeddings
    Map<String, Object> doc1 = new HashMap<>();
    doc1.put("id", "1");
    doc1.put("title", "Red Laptop");
    doc1.put("description", "Premium laptop");
    doc1.put("category", "electronics");
    doc1.put("price", 1200);
    doc1.put("text_embedding", new float[] {0.1f, 0.2f, 0.3f});
    doc1.put("image_embedding", new float[] {0.5f, 0.5f});

    Map<String, Object> doc2 = new HashMap<>();
    doc2.put("id", "2");
    doc2.put("title", "Blue Phone");
    doc2.put("description", "Budget smartphone");
    doc2.put("category", "electronics");
    doc2.put("price", 300);
    doc2.put("text_embedding", new float[] {0.4f, 0.5f, 0.6f});
    doc2.put("image_embedding", new float[] {0.3f, 0.4f});

    Map<String, Object> doc3 = new HashMap<>();
    doc3.put("id", "3");
    doc3.put("title", "Green Tablet");
    doc3.put("description", "Mid-range tablet");
    doc3.put("category", "electronics");
    doc3.put("price", 500);
    doc3.put("text_embedding", new float[] {0.7f, 0.8f, 0.9f});
    doc3.put("image_embedding", new float[] {0.1f, 0.2f});

    // Load all documents
    searchIndex.load(Arrays.asList(doc1, doc2, doc3), "id");

    // Wait for indexing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @AfterAll
  static void cleanupIndex() {
    if (searchIndex != null) {
      try {
        searchIndex.drop();
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("Should create multi-vector query with single vector")
  void testSingleVectorQuery() {
    Vector textVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(1.0)
            .build();

    MultiVectorQuery query = MultiVectorQuery.builder().vector(textVec).numResults(10).build();

    assertThat(query.getVectors()).hasSize(1);
    assertThat(query.getNumResults()).isEqualTo(10);

    Map<String, Object> params = query.toParams();
    assertThat(params).containsKey("vector_0");
    assertThat(params.get("vector_0")).isInstanceOf(byte[].class);
  }

  @Test
  @Order(2)
  @DisplayName("Should create multi-vector query with multiple vectors")
  void testMultipleVectorsQuery() {
    Vector textVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .fieldName("text_embedding")
            .weight(0.7)
            .build();

    Vector imageVec =
        Vector.builder()
            .vector(new float[] {0.5f, 0.5f})
            .fieldName("image_embedding")
            .weight(0.3)
            .build();

    MultiVectorQuery query =
        MultiVectorQuery.builder().vectors(textVec, imageVec).numResults(10).build();

    assertThat(query.getVectors()).hasSize(2);

    // Verify params
    Map<String, Object> params = query.toParams();
    assertThat(params).containsKeys("vector_0", "vector_1");

    // Verify query string format
    String queryString = query.toQueryString();
    assertThat(queryString)
        .contains("@text_embedding:[VECTOR_RANGE 2.0 $vector_0]")
        .contains("@image_embedding:[VECTOR_RANGE 2.0 $vector_1]")
        .contains(" | ");

    // Verify scoring
    String formula = query.getScoringFormula();
    assertThat(formula).contains("0.70 * score_0").contains("0.30 * score_1");
  }

  @Test
  @Order(3)
  @DisplayName("Should combine multi-vector query with filter expression")
  void testMultiVectorQueryWithFilter() {
    Vector textVec =
        Vector.builder().vector(new float[] {0.1f, 0.2f, 0.3f}).fieldName("text_embedding").build();

    Filter filter = Filter.tag("category", "electronics");

    MultiVectorQuery query =
        MultiVectorQuery.builder().vector(textVec).filterExpression(filter).numResults(5).build();

    String queryString = query.toQueryString();
    assertThat(queryString).contains(" AND ").contains("@category:{electronics}");
  }

  @Test
  @Order(4)
  @DisplayName("Should calculate score from multiple vectors with different weights")
  void testWeightedScoringCalculation() {
    Vector v1 =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .fieldName("text_embedding")
            .weight(0.6)
            .build();

    Vector v2 =
        Vector.builder()
            .vector(new float[] {0.5f, 0.5f})
            .fieldName("image_embedding")
            .weight(0.4)
            .build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(v1, v2).build();

    // Verify individual score calculations
    Map<String, String> calculations = query.getScoreCalculations();
    assertThat(calculations).hasSize(2);
    assertThat(calculations.get("score_0")).isEqualTo("(2 - distance_0)/2");
    assertThat(calculations.get("score_1")).isEqualTo("(2 - distance_1)/2");

    // Verify combined scoring formula
    String formula = query.getScoringFormula();
    assertThat(formula).isEqualTo("0.60 * score_0 + 0.40 * score_1");
  }

  @Test
  @Order(5)
  @DisplayName("Should support different vector dimensions and dtypes")
  void testDifferentDimensionsAndDtypes() {
    Vector v1 =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f}) // 3 dimensions
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.5)
            .build();

    Vector v2 =
        Vector.builder()
            .vector(new float[] {0.5f, 0.5f}) // 2 dimensions
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.5)
            .build();

    MultiVectorQuery query = MultiVectorQuery.builder().vectors(v1, v2).build();

    assertThat(query.getVectors().get(0).getVector()).hasSize(3);
    assertThat(query.getVectors().get(1).getVector()).hasSize(2);
  }

  @Test
  @Order(6)
  @DisplayName("Should specify return fields")
  void testReturnFields() {
    Vector textVec =
        Vector.builder().vector(new float[] {0.1f, 0.2f, 0.3f}).fieldName("text_embedding").build();

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vector(textVec)
            .returnFields("title", "price", "category")
            .build();

    assertThat(query.getReturnFields()).containsExactly("title", "price", "category");
  }

  @Test
  @Order(7)
  @DisplayName("Should use VECTOR_RANGE with threshold 2.0")
  void testVectorRangeThreshold() {
    Vector textVec =
        Vector.builder().vector(new float[] {0.1f, 0.2f, 0.3f}).fieldName("text_embedding").build();

    MultiVectorQuery query = MultiVectorQuery.builder().vector(textVec).build();

    String queryString = query.toQueryString();
    // Distance threshold hardcoded at 2.0 to include all eligible documents
    assertThat(queryString).contains("VECTOR_RANGE 2.0");
  }
}
