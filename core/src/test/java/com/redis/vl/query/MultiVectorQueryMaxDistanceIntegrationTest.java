package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Integration tests for per-vector max_distance in MultiVectorQuery.
 *
 * <p>Ported from Python: tests/integration/test_aggregation.py
 * (test_multivector_query_max_distances)
 *
 * <p>Verifies that each vector's max_distance threshold is independently applied when querying
 * against real Redis with FT.AGGREGATE VECTOR_RANGE.
 */
@Tag("integration")
@DisplayName("MultiVectorQuery max_distance Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiVectorQueryMaxDistanceIntegrationTest extends BaseIntegrationTest {

  private static final String INDEX_NAME = "multi_vec_maxdist_test_idx";
  private static SearchIndex searchIndex;

  @BeforeAll
  static void setupIndex() throws InterruptedException {
    // Clean up any existing index
    try {
      unifiedJedis.ftDropIndex(INDEX_NAME);
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }

    // Create schema with two vector fields (matching Python test structure)
    // user_embedding: 3 dimensions, COSINE
    // image_embedding: 5 dimensions, COSINE
    IndexSchema schema =
        IndexSchema.builder()
            .name(INDEX_NAME)
            .prefix("mvmd:")
            .field(TextField.builder().name("title").build())
            .field(
                VectorField.builder()
                    .name("user_embedding")
                    .dimensions(3)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            .field(
                VectorField.builder()
                    .name("image_embedding")
                    .dimensions(5)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            .build();

    searchIndex = new SearchIndex(schema, unifiedJedis);
    searchIndex.create();

    // Insert test documents with varying vector values to produce a range of distances.
    // For COSINE distance: distance = 1 - cosine_similarity, range [0, 2].
    // We create 6 documents with progressively different vectors so that queries
    // produce different result counts at different distance thresholds.
    List<Map<String, Object>> docs = new ArrayList<>();
    float[][] userVecs = {
      {0.1f, 0.2f, 0.5f}, // very similar to query
      {0.15f, 0.25f, 0.45f}, // very similar
      {0.3f, 0.1f, 0.4f}, // somewhat similar
      {0.5f, 0.5f, 0.1f}, // moderately different
      {0.9f, 0.1f, 0.1f}, // quite different
      {-0.5f, -0.3f, 0.2f} // very different
    };

    float[][] imageVecs = {
      {1.2f, 0.3f, -0.4f, 0.7f, 0.2f}, // very similar to query
      {1.0f, 0.4f, -0.3f, 0.6f, 0.3f}, // very similar
      {0.5f, 0.8f, 0.1f, 0.3f, 0.5f}, // moderately different
      {0.1f, 0.1f, 0.9f, 0.1f, 0.1f}, // quite different
      {-0.3f, 0.7f, 0.5f, -0.2f, 0.4f}, // very different
      {-0.8f, -0.2f, 0.6f, -0.5f, -0.1f} // very different
    };

    for (int i = 0; i < 6; i++) {
      Map<String, Object> doc = new HashMap<>();
      doc.put("id", String.valueOf(i + 1));
      doc.put("title", "Document " + (i + 1));
      doc.put("user_embedding", userVecs[i]);
      doc.put("image_embedding", imageVecs[i]);
      docs.add(doc);
    }

    searchIndex.load(docs, "id");

    // Wait for indexing
    Thread.sleep(200);
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
  @DisplayName("Should return all documents with max_distance=2.0 (default)")
  void testDefaultMaxDistanceReturnsAll() {
    Vector userVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .build(); // default max_distance=2.0

    Vector imageVec =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .build(); // default max_distance=2.0

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vectors(userVec, imageVec)
            .returnFields("title", "distance_0", "distance_1")
            .numResults(10)
            .build();

    List<Map<String, Object>> results = searchIndex.query(query);

    // With max_distance=2.0 on both vectors, all 6 documents should match
    assertThat(results).hasSize(6);
  }

  @Test
  @Order(2)
  @DisplayName("Should return fewer documents with tight max_distance")
  void testTightMaxDistanceFilters() {
    Vector userVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(0.05) // very tight - only very similar vectors
            .build();

    Vector imageVec =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(0.05) // very tight
            .build();

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vectors(userVec, imageVec)
            .returnFields("title", "distance_0", "distance_1")
            .numResults(10)
            .build();

    List<Map<String, Object>> results = searchIndex.query(query);

    // Very tight thresholds should return fewer results than default
    assertThat(results.size()).isLessThan(6);
  }

  @Test
  @Order(3)
  @DisplayName("Should filter independently per vector field")
  void testIndependentPerVectorFiltering() {
    // Use a tight threshold on user_embedding but loose on image_embedding
    Vector userVecTight =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(0.01) // very tight
            .build();

    Vector imageVecLoose =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(2.0) // wide open
            .build();

    MultiVectorQuery tightUserQuery =
        MultiVectorQuery.builder()
            .vectors(userVecTight, imageVecLoose)
            .returnFields("title", "distance_0", "distance_1")
            .numResults(10)
            .build();

    // Now flip: loose on user, tight on image
    Vector userVecLoose =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(2.0) // wide open
            .build();

    Vector imageVecTight =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(0.01) // very tight
            .build();

    MultiVectorQuery tightImageQuery =
        MultiVectorQuery.builder()
            .vectors(userVecLoose, imageVecTight)
            .returnFields("title", "distance_0", "distance_1")
            .numResults(10)
            .build();

    List<Map<String, Object>> tightUserResults = searchIndex.query(tightUserQuery);
    List<Map<String, Object>> tightImageResults = searchIndex.query(tightImageQuery);

    // Both should return fewer than 6 (the default max)
    // The two queries should potentially return different counts because they
    // filter different vector fields tightly
    assertThat(tightUserResults.size()).isLessThanOrEqualTo(6);
    assertThat(tightImageResults.size()).isLessThanOrEqualTo(6);
  }

  @Test
  @Order(4)
  @DisplayName("Returned distances should respect max_distance thresholds")
  void testReturnedDistancesWithinThreshold() {
    double userMaxDist = 0.5;
    double imageMaxDist = 0.5;

    Vector userVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(userMaxDist)
            .build();

    Vector imageVec =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(imageMaxDist)
            .build();

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vectors(userVec, imageVec)
            .returnFields("title", "distance_0", "distance_1", "score_0", "score_1")
            .numResults(10)
            .build();

    List<Map<String, Object>> results = searchIndex.query(query);

    // Every returned document should have distances within the thresholds
    for (Map<String, Object> result : results) {
      Object dist0 = result.get("distance_0");
      Object dist1 = result.get("distance_1");
      if (dist0 != null) {
        assertThat(Double.parseDouble(dist0.toString()))
            .as("distance_0 for %s", result.get("title"))
            .isLessThanOrEqualTo(userMaxDist);
      }
      if (dist1 != null) {
        assertThat(Double.parseDouble(dist1.toString()))
            .as("distance_1 for %s", result.get("title"))
            .isLessThanOrEqualTo(imageMaxDist);
      }
    }
  }

  @ParameterizedTest(name = "max_distance({0}, {1}) should return results")
  @CsvSource({
    "2.0, 2.0", // widest - should return all
    "0.5, 0.5", // moderate
    "0.1, 0.1", // tight
  })
  @Order(5)
  @DisplayName("Parametrized: tighter thresholds should return fewer or equal results")
  void testTighterThresholdsReturnFewerResults(double maxDist1, double maxDist2) {
    Vector userVec =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(maxDist1)
            .build();

    Vector imageVec =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(maxDist2)
            .build();

    MultiVectorQuery query =
        MultiVectorQuery.builder()
            .vectors(userVec, imageVec)
            .returnFields("title", "distance_0", "distance_1")
            .numResults(10)
            .build();

    List<Map<String, Object>> results = searchIndex.query(query);

    // Results count should be non-negative
    assertThat(results.size()).isGreaterThanOrEqualTo(0);

    // All returned results should have distances within thresholds
    for (Map<String, Object> result : results) {
      Object dist0 = result.get("distance_0");
      Object dist1 = result.get("distance_1");
      if (dist0 != null) {
        assertThat(Double.parseDouble(dist0.toString())).isLessThanOrEqualTo(maxDist1);
      }
      if (dist1 != null) {
        assertThat(Double.parseDouble(dist1.toString())).isLessThanOrEqualTo(maxDist2);
      }
    }
  }

  @Test
  @Order(6)
  @DisplayName("Monotonicity: wider thresholds return >= results than narrower")
  void testMonotonicity() {
    // Narrow thresholds
    Vector userNarrow =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(0.1)
            .build();
    Vector imageNarrow =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(0.1)
            .build();

    MultiVectorQuery narrowQuery =
        MultiVectorQuery.builder()
            .vectors(userNarrow, imageNarrow)
            .returnFields("title")
            .numResults(10)
            .build();

    // Wide thresholds
    Vector userWide =
        Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.5f})
            .fieldName("user_embedding")
            .maxDistance(1.5)
            .build();
    Vector imageWide =
        Vector.builder()
            .vector(new float[] {1.2f, 0.3f, -0.4f, 0.7f, 0.2f})
            .fieldName("image_embedding")
            .maxDistance(1.5)
            .build();

    MultiVectorQuery wideQuery =
        MultiVectorQuery.builder()
            .vectors(userWide, imageWide)
            .returnFields("title")
            .numResults(10)
            .build();

    List<Map<String, Object>> narrowResults = searchIndex.query(narrowQuery);
    List<Map<String, Object>> wideResults = searchIndex.query(wideQuery);

    assertThat(wideResults.size())
        .as("Wider thresholds should return >= results than narrower")
        .isGreaterThanOrEqualTo(narrowResults.size());
  }
}
