package com.redis.vl.notebooks;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.AggregateHybridQuery;
import com.redis.vl.query.Filter;
import com.redis.vl.query.HybridQuery;
import com.redis.vl.query.MultiVectorQuery;
import com.redis.vl.query.TextQuery;
import com.redis.vl.schema.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * Port of Python notebook: docs/user_guide/11_advanced_queries.ipynb
 *
 * <p>Demonstrates advanced query types available in RedisVL:
 *
 * <ol>
 *   <li><b>TextQuery</b>: Full text search with advanced scoring algorithms (BM25, TFIDF)
 *   <li><b>AggregateHybridQuery</b>: Combines text and vector search for hybrid retrieval
 *   <li><b>MultiVectorQuery</b>: Search over multiple vector fields simultaneously
 * </ol>
 *
 * <p>Python reference: /Users/brian.sam-bodden/Code/redis/py/redis-vl-python/docs/user_guide/11_advanced_queries.ipynb
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedQueriesNotebookIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;
  private static final String INDEX_NAME = "advanced_queries";
  private static final String PREFIX = "products";

  /**
   * Helper method to convert float array to byte array for vector fields.
   * Matches Python: np.array([...], dtype=np.float32).tobytes()
   */
  private byte[] floatArrayToBytes(float[] vector) {
    ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : vector) {
      buffer.putFloat(value);
    }
    return buffer.array();
  }

  @BeforeEach
  void setUp() {
    // Python schema from cells 4-5
    IndexSchema schema =
        IndexSchema.builder()
            .name(INDEX_NAME)
            .prefix(PREFIX)
            .storageType(IndexSchema.StorageType.HASH)
            .field(TagField.builder().name("product_id").build())
            .field(TagField.builder().name("category").build())
            .field(TextField.builder().name("brief_description").build())
            .field(TextField.builder().name("full_description").build())
            .field(NumericField.builder().name("price").build())
            .field(NumericField.builder().name("rating").build())
            .field(
                VectorField.builder()
                    .name("text_embedding")
                    .dimensions(3)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            .field(
                VectorField.builder()
                    .name("image_embedding")
                    .dimensions(2)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .build())
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    index.create(true);

    // Load sample product data from Python cell 2
    loadSampleData();
  }

  private void loadSampleData() {
    // Python sample data from cell 2
    List<Map<String, Object>> products =
        Arrays.asList(
            createProduct(
                "prod_1",
                "comfortable running shoes for athletes",
                "Engineered with a dual-layer EVA foam midsole and FlexWeave breathable mesh upper",
                "footwear",
                89.99,
                4.5,
                new float[] {0.1f, 0.2f, 0.1f},
                new float[] {0.8f, 0.1f}),
            createProduct(
                "prod_2",
                "lightweight running jacket with water resistance",
                "Stay protected with this ultralight 2.5-layer DWR-coated shell featuring laser-cut ventilation",
                "outerwear",
                129.99,
                4.8,
                new float[] {0.2f, 0.3f, 0.2f},
                new float[] {0.7f, 0.2f}),
            createProduct(
                "prod_3",
                "professional tennis racket for competitive players",
                "Competition-grade racket featuring a 98 sq in head size, 16x19 string pattern",
                "equipment",
                199.99,
                4.9,
                new float[] {0.9f, 0.1f, 0.05f},
                new float[] {0.1f, 0.9f}),
            createProduct(
                "prod_4",
                "yoga mat with extra cushioning for comfort",
                "Premium 8mm thick TPE yoga mat with dual-texture surface",
                "accessories",
                39.99,
                4.3,
                new float[] {0.15f, 0.25f, 0.15f},
                new float[] {0.5f, 0.5f}),
            createProduct(
                "prod_5",
                "basketball shoes with excellent ankle support",
                "High-top basketball sneakers with Zoom Air units in forefoot and heel",
                "footwear",
                139.99,
                4.7,
                new float[] {0.12f, 0.18f, 0.12f},
                new float[] {0.75f, 0.15f}),
            createProduct(
                "prod_6",
                "swimming goggles with anti-fog coating",
                "Low-profile competition goggles with curved polycarbonate lenses",
                "accessories",
                24.99,
                4.4,
                new float[] {0.3f, 0.1f, 0.2f},
                new float[] {0.2f, 0.8f}));

    // Load using direct hash operations (matching Python's approach)
    for (Map<String, Object> product : products) {
      String key = PREFIX + ":" + product.get("product_id");
      Map<String, String> fields = new HashMap<>();
      product.forEach(
          (k, v) -> {
            if (v instanceof byte[]) {
              fields.put(k, new String((byte[]) v, java.nio.charset.StandardCharsets.ISO_8859_1));
            } else {
              fields.put(k, String.valueOf(v));
            }
          });
      unifiedJedis.hset(key, fields);
    }
  }

  private Map<String, Object> createProduct(
      String productId,
      String briefDesc,
      String fullDesc,
      String category,
      double price,
      double rating,
      float[] textEmbedding,
      float[] imageEmbedding) {
    Map<String, Object> product = new HashMap<>();
    product.put("product_id", productId);
    product.put("brief_description", briefDesc);
    product.put("full_description", fullDesc);
    product.put("category", category);
    product.put("price", price);
    product.put("rating", rating);
    product.put("text_embedding", floatArrayToBytes(textEmbedding));
    product.put("image_embedding", floatArrayToBytes(imageEmbedding));
    return product;
  }

  @AfterEach
  void tearDown() {
    if (index != null) {
      index.delete(true);
    }
  }

  /**
   * ## 1. TextQuery: Full Text Search
   *
   * <p>Python cell 8: Basic text search for "running shoes"
   */
  @Test
  @Order(1)
  @DisplayName("Basic Text Search - Python cell 8")
  void testBasicTextSearch() {
    // Python: TextQuery(text="running shoes", text_field_name="brief_description", ...)
    TextQuery query =
        TextQuery.builder()
            .text("running shoes")
            .textField("brief_description")
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(query);

    assertThat(results).isNotEmpty();
    // Should find prod_1 (running shoes) and prod_2 (running jacket)
    assertThat(results).anyMatch(doc -> "prod_1".equals(doc.get("product_id")));
  }

  /**
   * ### Text Search with Different Scoring Algorithms
   *
   * <p>Python cells 10-11: Compare BM25STD and TFIDF scorers
   */
  @Test
  @Order(2)
  @DisplayName("Text Search with Different Scoring Algorithms - Python cells 10-11")
  void testTextSearchWithDifferentScorers() {
    // Python cell 10: BM25 scoring
    TextQuery bm25Query =
        TextQuery.builder()
            .text("comfortable shoes")
            .textField("brief_description")
            .scorer("BM25STD") // Python: text_scorer="BM25STD"
            .numResults(3)
            .build();

    List<Map<String, Object>> bm25Results = index.query(bm25Query);
    assertThat(bm25Results).isNotEmpty();

    // Python cell 11: TFIDF scoring
    TextQuery tfidfQuery =
        TextQuery.builder()
            .text("comfortable shoes")
            .textField("brief_description")
            .scorer("TFIDF") // Python: text_scorer="TFIDF"
            .numResults(3)
            .build();

    List<Map<String, Object>> tfidfResults = index.query(tfidfQuery);
    assertThat(tfidfResults).isNotEmpty();

    // Both scorers should return results, though potentially in different orders
    assertThat(bm25Results).hasSizeGreaterThan(0);
    assertThat(tfidfResults).hasSizeGreaterThan(0);
  }

  /**
   * ### Text Search with Filters
   *
   * <p>Python cells 13-14: Combine text search with tag and numeric filters
   */
  @Test
  @Order(3)
  @DisplayName("Text Search with Filters - Python cells 13-14")
  void testTextSearchWithFilters() {
    // Python cell 13: Search for "shoes" only in footwear category
    // filter_expression=Tag("category") == "footwear"
    TextQuery categoryFilterQuery =
        TextQuery.builder()
            .text("shoes")
            .textField("brief_description")
            .filterExpression(Filter.tag("category", "footwear"))
            .numResults(5)
            .build();

    List<Map<String, Object>> categoryResults = index.query(categoryFilterQuery);

    assertThat(categoryResults).isNotEmpty();
    // Verify all results are in footwear category
    assertThat(categoryResults).allMatch(doc -> "footwear".equals(doc.get("category")));

    // Python cell 14: Search for products under $100
    // filter_expression=Num("price") < 100
    TextQuery priceFilterQuery =
        TextQuery.builder()
            .text("comfortable")
            .textField("brief_description")
            .filterExpression(Filter.numeric("price").lt(100))
            .numResults(5)
            .build();

    List<Map<String, Object>> priceResults = index.query(priceFilterQuery);
    assertThat(priceResults).isNotEmpty();
    // Verify all results are under $100
    assertThat(priceResults)
        .allMatch(
            doc -> {
              Object priceObj = doc.get("price");
              double price =
                  priceObj instanceof Number
                      ? ((Number) priceObj).doubleValue()
                      : Double.parseDouble(priceObj.toString());
              return price < 100;
            });
  }

  /**
   * ### Text Search with Multiple Fields and Weights
   *
   * <p>Python cell 16: Prioritize brief_description (1.0) over full_description (0.5)
   */
  @Test
  @Order(4)
  @DisplayName("Text Search with Field Weights - Python cell 16")
  void testTextSearchWithWeights() {
    // Python: text_field_name={"brief_description": 1.0, "full_description": 0.5}
    Map<String, Double> fieldWeights = Map.of("brief_description", 1.0, "full_description", 0.5);

    TextQuery weightedQuery =
        TextQuery.builder()
            .text("shoes")
            .textFieldWeights(fieldWeights)
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(weightedQuery);

    assertThat(results).isNotEmpty();
    // Should prioritize matches in brief_description
  }

  /**
   * ## 2. AggregateHybridQuery: Combining Text and Vector Search
   *
   * <p>Python cell 23: Basic hybrid query combining text and vector search
   */
  @Test
  @Order(5)
  @DisplayName("Basic Aggregate Hybrid Query - Python cell 23")
  void testBasicAggregateHybridQuery() {
    // Python: AggregateHybridQuery(text="running shoes", text_field_name="brief_description",
    //         vector=[0.1, 0.2, 0.1], vector_field_name="text_embedding", ...)
    HybridQuery hybridQuery =
        AggregateHybridQuery.builder()
            .text("running shoes")
            .textFieldName("brief_description")
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .vectorFieldName("text_embedding")
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(hybridQuery);

    assertThat(results).isNotEmpty();
    // Should combine text matching (running, shoes) with vector similarity
    assertThat(results).anyMatch(doc -> "prod_1".equals(doc.get("product_id")));
  }

  /**
   * ### Adjusting the Alpha Parameter
   *
   * <p>Python cell 25: Emphasize vector search with alpha=0.9 (90% vector, 10% text)
   */
  @Test
  @Order(6)
  @DisplayName("Hybrid Query with Alpha Parameter - Python cell 25")
  void testHybridQueryWithAlpha() {
    // Python: alpha=0.9 (90% vector, 10% text)
    HybridQuery vectorHeavyQuery =
        AggregateHybridQuery.builder()
            .text("comfortable")
            .textFieldName("brief_description")
            .vector(new float[] {0.15f, 0.25f, 0.15f})
            .vectorFieldName("text_embedding")
            .alpha(0.9f) // 90% vector, 10% text
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(vectorHeavyQuery);

    assertThat(results).isNotEmpty();
    // Results should prioritize vector similarity over text matching
  }

  /**
   * ### Aggregate Hybrid Query with Filters
   *
   * <p>Python cell 27: Hybrid search with price filter
   */
  @Test
  @Order(7)
  @DisplayName("Hybrid Query with Filters - Python cell 27")
  void testHybridQueryWithFilters() {
    // Python: filter_expression=Num("price") > 100
    HybridQuery filteredHybridQuery =
        AggregateHybridQuery.builder()
            .text("professional equipment")
            .textFieldName("brief_description")
            .vector(new float[] {0.9f, 0.1f, 0.05f})
            .vectorFieldName("text_embedding")
            .filterExpression(Filter.numeric("price").gt(100))
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(filteredHybridQuery);

    assertThat(results).isNotEmpty();
    // Verify all results have price > $100
    assertThat(results)
        .allMatch(
            doc -> {
              Object priceObj = doc.get("price");
              double price =
                  priceObj instanceof Number
                      ? ((Number) priceObj).doubleValue()
                      : Double.parseDouble(priceObj.toString());
              return price > 100;
            });
  }

  /**
   * ### Using Different Text Scorers
   *
   * <p>Python cell 29: Hybrid query with TFIDF scorer
   */
  @Test
  @Order(8)
  @DisplayName("Hybrid Query with TFIDF Scorer - Python cell 29")
  void testHybridQueryWithTFIDF() {
    // Python: text_scorer="TFIDF"
    HybridQuery hybridTfidf =
        AggregateHybridQuery.builder()
            .text("shoes support")
            .textFieldName("brief_description")
            .vector(new float[] {0.12f, 0.18f, 0.12f})
            .vectorFieldName("text_embedding")
            .textScorer("TFIDF")
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(hybridTfidf);

    assertThat(results).isNotEmpty();
    // Should use TFIDF for text scoring combined with vector similarity
  }

  /**
   * ## 3. MultiVectorQuery: Multi-Vector Search
   *
   * <p>Python cell 32: Search over multiple vector fields (text + image embeddings)
   */
  @Test
  @Order(9)
  @DisplayName("Basic Multi-Vector Query - Python cell 32")
  void testBasicMultiVectorQuery() {
    // Python:
    // text_vector = Vector(vector=[0.1, 0.2, 0.1], field_name="text_embedding", weight=0.7)
    // image_vector = Vector(vector=[0.8, 0.1], field_name="image_embedding", weight=0.3)
    com.redis.vl.query.Vector textVector =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.7) // 70% weight for text embedding
            .build();

    com.redis.vl.query.Vector imageVector =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.8f, 0.1f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.3) // 30% weight for image embedding
            .build();

    MultiVectorQuery multiQuery =
        MultiVectorQuery.builder().vectors(textVector, imageVector).numResults(5).build();

    List<Map<String, Object>> results = index.query(multiQuery);

    assertThat(results).isNotEmpty();
    // Should return results ranked by combined score: 0.7 * text_score + 0.3 * image_score
  }

  /**
   * ### Adjusting Vector Weights
   *
   * <p>Python cell 34: Emphasize image similarity (80% image, 20% text)
   */
  @Test
  @Order(10)
  @DisplayName("Multi-Vector Query with Different Weights - Python cell 34")
  void testMultiVectorQueryWithDifferentWeights() {
    // Python: More emphasis on image similarity
    com.redis.vl.query.Vector textVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.9f, 0.1f, 0.05f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.2) // 20% weight
            .build();

    com.redis.vl.query.Vector imageVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.9f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.8) // 80% weight
            .build();

    MultiVectorQuery imageHeavyQuery =
        MultiVectorQuery.builder().vectors(textVec, imageVec).numResults(3).build();

    List<Map<String, Object>> results = index.query(imageHeavyQuery);

    assertThat(results).isNotEmpty();
    // Results prioritize image similarity
  }

  /**
   * ### Multi-Vector Query with Filters
   *
   * <p>Python cell 36: Combine multi-vector search with category filter
   */
  @Test
  @Order(11)
  @DisplayName("Multi-Vector Query with Filters - Python cell 36")
  void testMultiVectorQueryWithFilters() {
    // Python: filter_expression=Tag("category") == "footwear"
    com.redis.vl.query.Vector textVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.6)
            .build();

    com.redis.vl.query.Vector imageVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.8f, 0.1f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.4)
            .build();

    MultiVectorQuery filteredMultiQuery =
        MultiVectorQuery.builder()
            .vectors(textVec, imageVec)
            .filterExpression(Filter.tag("category", "footwear"))
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(filteredMultiQuery);

    assertThat(results).isNotEmpty();
    // Verify all results are in footwear category
    assertThat(results).allMatch(doc -> "footwear".equals(doc.get("category")));
  }

  /**
   * ## Comparing Query Types
   *
   * <p>Python cells 38-40: Side-by-side comparison of TextQuery and MultiVectorQuery
   */
  @Test
  @Order(12)
  @DisplayName("Comparing Query Types - Python cells 38-40")
  void testCompareQueryTypes() {
    // Python cell 38: TextQuery - keyword-based search
    TextQuery textQuery =
        TextQuery.builder().text("shoes").textField("brief_description").numResults(3).build();

    List<Map<String, Object>> textResults = index.query(textQuery);
    assertThat(textResults).isNotEmpty();

    // Python cell 40: MultiVectorQuery - searches multiple vector fields
    com.redis.vl.query.Vector mvText =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.5)
            .build();

    com.redis.vl.query.Vector mvImage =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.8f, 0.1f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.5)
            .build();

    MultiVectorQuery multiQuery =
        MultiVectorQuery.builder().vectors(mvText, mvImage).numResults(3).build();

    List<Map<String, Object>> multiResults = index.query(multiQuery);
    assertThat(multiResults).isNotEmpty();

    // All query types should return results
    assertThat(textResults).hasSizeGreaterThan(0);
    assertThat(multiResults).hasSizeGreaterThan(0);
  }
}
