package com.redis.vl.notebooks;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.*;
import com.redis.vl.schema.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration test that mirrors notebooks/11_advanced_queries.ipynb exactly.
 *
 * <p>Each test method corresponds to one or more code cells in the notebook. If a test fails here,
 * the corresponding notebook cell will also fail.
 *
 * <p>Notebook sections:
 *
 * <ol>
 *   <li>TextQuery: Full text search with scoring, filters, weights, and returnFields
 *   <li>HybridQuery (FT.HYBRID) and AggregateHybridQuery (FT.AGGREGATE): Hybrid text+vector search
 *   <li>MultiVectorQuery: Multi-vector search over multiple embedding fields
 * </ol>
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedQueriesNotebookIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;
  private static final String INDEX_NAME = "advanced_queries";
  private static final String PREFIX = "products";

  /** Notebook cell: helper-methods */
  private byte[] floatArrayToBytes(float[] vector) {
    ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : vector) {
      buffer.putFloat(value);
    }
    return buffer.array();
  }

  /** Notebook cells: schema + load-data */
  @BeforeEach
  void setUp() {
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
    loadSampleData();
  }

  /** Notebook cell: sample-data — identical product data */
  private void loadSampleData() {
    List<Map<String, Object>> products =
        Arrays.asList(
            createProduct(
                "prod_1",
                "comfortable running shoes for athletes",
                "Engineered with a dual-layer EVA foam midsole and FlexWeave breathable mesh upper, these running shoes deliver responsive cushioning for long-distance runs. The anatomical footbed adapts to your stride while the carbon rubber outsole provides superior traction on varied terrain.",
                "footwear",
                89.99,
                4.5,
                new float[] {0.1f, 0.2f, 0.1f},
                new float[] {0.8f, 0.1f}),
            createProduct(
                "prod_2",
                "lightweight running jacket with water resistance",
                "Stay protected with this ultralight 2.5-layer DWR-coated shell featuring laser-cut ventilation zones and reflective piping for low-light visibility. Packs into its own chest pocket and weighs just 4.2 oz, making it ideal for unpredictable weather conditions.",
                "outerwear",
                129.99,
                4.8,
                new float[] {0.2f, 0.3f, 0.2f},
                new float[] {0.7f, 0.2f}),
            createProduct(
                "prod_3",
                "professional tennis racket for competitive players",
                "Competition-grade racket featuring a 98 sq in head size, 16x19 string pattern, and aerospace-grade graphite frame that delivers explosive power with pinpoint control. Tournament-approved specs include 315g weight and 68 RA stiffness rating for advanced baseline play.",
                "equipment",
                199.99,
                4.9,
                new float[] {0.9f, 0.1f, 0.05f},
                new float[] {0.1f, 0.9f}),
            createProduct(
                "prod_4",
                "yoga mat with extra cushioning for comfort",
                "Premium 8mm thick TPE yoga mat with dual-texture surface - smooth side for hot yoga flow and textured side for maximum grip during balancing poses. Closed-cell technology prevents moisture absorption while alignment markers guide proper positioning in asanas.",
                "accessories",
                39.99,
                4.3,
                new float[] {0.15f, 0.25f, 0.15f},
                new float[] {0.5f, 0.5f}),
            createProduct(
                "prod_5",
                "basketball shoes with excellent ankle support",
                "High-top basketball sneakers with Zoom Air units in forefoot and heel, reinforced lateral sidewalls for explosive cuts, and herringbone traction pattern optimized for hardwood courts. The internal bootie construction and extended ankle collar provide lockdown support during aggressive drives.",
                "footwear",
                139.99,
                4.7,
                new float[] {0.12f, 0.18f, 0.12f},
                new float[] {0.75f, 0.15f}),
            createProduct(
                "prod_6",
                "swimming goggles with anti-fog coating",
                "Low-profile competition goggles with curved polycarbonate lenses offering 180-degree peripheral vision and UV protection. Hydrophobic anti-fog coating lasts 10x longer than standard treatments, while the split silicone strap and interchangeable nose bridges ensure a watertight, custom fit.",
                "accessories",
                24.99,
                4.4,
                new float[] {0.3f, 0.1f, 0.2f},
                new float[] {0.2f, 0.8f}));

    index.load(products, "product_id");
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

  // ===== Section 1: TextQuery =====

  /** Notebook cell: text-query-basic */
  @Test
  @Order(1)
  @DisplayName("TextQuery basic search with returnFields")
  void testTextQueryBasic() {
    TextQuery textQuery =
        TextQuery.builder()
            .text("running shoes")
            .textField("brief_description")
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(textQuery);

    assertThat(results).isNotEmpty();
    assertThat(results).anyMatch(doc -> "prod_1".equals(doc.get("product_id")));
    // Verify returnFields works — results should contain only the requested fields (plus id)
    for (Map<String, Object> result : results) {
      assertThat(result).containsKey("product_id");
      assertThat(result).containsKey("brief_description");
    }
  }

  /** Notebook cell: text-query-bm25 */
  @Test
  @Order(2)
  @DisplayName("TextQuery with BM25 scorer and returnFields")
  void testTextQueryBM25() {
    TextQuery bm25Query =
        TextQuery.builder()
            .text("comfortable shoes")
            .textField("brief_description")
            .scorer("BM25STD")
            .returnFields(Arrays.asList("product_id", "brief_description", "price"))
            .numResults(3)
            .build();

    List<Map<String, Object>> bm25Results = index.query(bm25Query);
    assertThat(bm25Results).isNotEmpty();
    assertThat(bm25Results).anyMatch(doc -> "prod_1".equals(doc.get("product_id")));
  }

  /** Notebook cell: text-query-tfidf */
  @Test
  @Order(3)
  @DisplayName("TextQuery with TFIDF scorer and returnFields")
  void testTextQueryTFIDF() {
    TextQuery tfidfQuery =
        TextQuery.builder()
            .text("comfortable shoes")
            .textField("brief_description")
            .scorer("TFIDF")
            .returnFields(Arrays.asList("product_id", "brief_description", "price"))
            .numResults(3)
            .build();

    List<Map<String, Object>> tfidfResults = index.query(tfidfQuery);
    assertThat(tfidfResults).isNotEmpty();
  }

  /** Notebook cell: text-query-filter-tag */
  @Test
  @Order(4)
  @DisplayName("TextQuery with tag filter and returnFields")
  void testTextQueryFilterTag() {
    TextQuery filteredTextQuery =
        TextQuery.builder()
            .text("shoes")
            .textField("brief_description")
            .filterExpression(Filter.tag("category", "footwear"))
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> filteredResults = index.query(filteredTextQuery);

    assertThat(filteredResults).isNotEmpty();
    assertThat(filteredResults).allMatch(doc -> "footwear".equals(doc.get("category")));
  }

  /** Notebook cell: text-query-filter-numeric */
  @Test
  @Order(5)
  @DisplayName("TextQuery with numeric filter and returnFields")
  void testTextQueryFilterNumeric() {
    TextQuery priceFilteredQuery =
        TextQuery.builder()
            .text("comfortable")
            .textField("brief_description")
            .filterExpression(Filter.numeric("price").lt(100))
            .returnFields(Arrays.asList("product_id", "brief_description", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> priceResults = index.query(priceFilteredQuery);
    assertThat(priceResults).isNotEmpty();
    assertThat(priceResults)
        .allMatch(
            doc -> {
              double price = Double.parseDouble(doc.get("price").toString());
              return price < 100;
            });
  }

  /** Notebook cell: text-query-weighted */
  @Test
  @Order(6)
  @DisplayName("TextQuery with weighted fields and returnFields")
  void testTextQueryWeighted() {
    Map<String, Double> fieldWeights = new HashMap<>();
    fieldWeights.put("brief_description", 1.0);
    fieldWeights.put("full_description", 0.5);

    TextQuery weightedQuery =
        TextQuery.builder()
            .text("shoes")
            .textFieldWeights(fieldWeights)
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .build();

    List<Map<String, Object>> weightedResults = index.query(weightedQuery);
    assertThat(weightedResults).isNotEmpty();
  }

  // ===== Section 2: HybridQuery (FT.HYBRID) =====

  /** Notebook cell: hybrid-query-basic */
  @Test
  @Order(7)
  @DisplayName("HybridQuery basic with LINEAR combination")
  void testHybridQueryBasic() {
    HybridQuery hybridQuery =
        HybridQuery.builder()
            .text("running shoes")
            .textFieldName("brief_description")
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .vectorFieldName("text_embedding")
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> hybridResults = index.query(hybridQuery);

    assertThat(hybridResults).isNotEmpty();
    assertThat(hybridResults).anyMatch(doc -> "prod_1".equals(doc.get("product_id")));
  }

  /** Notebook cell: rdih4aduzml — AggregateHybridQuery fallback */
  @Test
  @Order(8)
  @DisplayName("AggregateHybridQuery basic (FT.AGGREGATE)")
  void testAggregateHybridQueryBasic() {
    AggregateHybridQuery aggHybridQuery =
        AggregateHybridQuery.builder()
            .text("running shoes")
            .textFieldName("brief_description")
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .vectorFieldName("text_embedding")
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> aggResults = index.query(aggHybridQuery);

    assertThat(aggResults).isNotEmpty();
    assertThat(aggResults).allMatch(doc -> doc.containsKey("hybrid_score"));
    assertThat(aggResults).allMatch(doc -> doc.containsKey("text_score"));
    assertThat(aggResults).allMatch(doc -> doc.containsKey("vector_similarity"));
  }

  /** Notebook cell: hybrid-query-alpha-code — HybridQuery with linearAlpha */
  @Test
  @Order(9)
  @DisplayName("HybridQuery with linearAlpha=0.1 (vector-heavy)")
  void testHybridQueryLinearAlpha() {
    HybridQuery vectorHeavyQuery =
        HybridQuery.builder()
            .text("comfortable")
            .textFieldName("brief_description")
            .vector(new float[] {0.15f, 0.25f, 0.15f})
            .vectorFieldName("text_embedding")
            .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
            .linearAlpha(0.1f) // 10% text, 90% vector
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> results = index.query(vectorHeavyQuery);
    assertThat(results).isNotEmpty();
  }

  /** Notebook cell: euln4vqwvg — AggregateHybridQuery with alpha=0.9 */
  @Test
  @Order(10)
  @DisplayName("AggregateHybridQuery with alpha=0.9 (vector-heavy)")
  void testAggregateHybridQueryAlpha() {
    AggregateHybridQuery vectorHeavyAggQuery =
        AggregateHybridQuery.builder()
            .text("comfortable")
            .textFieldName("brief_description")
            .vector(new float[] {0.15f, 0.25f, 0.15f})
            .vectorFieldName("text_embedding")
            .alpha(0.9f) // 90% vector, 10% text
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(vectorHeavyAggQuery);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(doc -> doc.containsKey("hybrid_score"));
  }

  /** Notebook cell: agr8k3jy1ip — HybridQuery with RRF combination */
  @Test
  @Order(11)
  @DisplayName("HybridQuery with RRF combination method")
  void testHybridQueryRRF() {
    HybridQuery rrfQuery =
        HybridQuery.builder()
            .text("comfortable")
            .textFieldName("brief_description")
            .vector(new float[] {0.15f, 0.25f, 0.15f})
            .vectorFieldName("text_embedding")
            .combinationMethod(HybridQuery.CombinationMethod.RRF)
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> rrfResults = index.query(rrfQuery);
    assertThat(rrfResults).isNotEmpty();
  }

  /** Notebook cell: hybrid-query-filter-code — HybridQuery with filter */
  @Test
  @Order(12)
  @DisplayName("HybridQuery with price filter")
  void testHybridQueryWithFilter() {
    HybridQuery filteredHybridQuery =
        HybridQuery.builder()
            .text("professional equipment")
            .textFieldName("brief_description")
            .vector(new float[] {0.9f, 0.1f, 0.05f})
            .vectorFieldName("text_embedding")
            .filterExpression(Filter.numeric("price").gt(100))
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> results = index.query(filteredHybridQuery);
    assertThat(results).isNotEmpty();
  }

  /** Notebook cell: p2yl7z26pd — AggregateHybridQuery with filter */
  @Test
  @Order(13)
  @DisplayName("AggregateHybridQuery with price filter")
  void testAggregateHybridQueryWithFilter() {
    AggregateHybridQuery filteredAggHybridQuery =
        AggregateHybridQuery.builder()
            .text("professional equipment")
            .textFieldName("brief_description")
            .vector(new float[] {0.9f, 0.1f, 0.05f})
            .vectorFieldName("text_embedding")
            .filterExpression(Filter.numeric("price").gt(100))
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(filteredAggHybridQuery);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(doc -> doc.containsKey("hybrid_score"));
  }

  /** Notebook cell: hybrid-query-scorer-code — HybridQuery with TFIDF scorer */
  @Test
  @Order(14)
  @DisplayName("HybridQuery with TFIDF scorer")
  void testHybridQueryTFIDFScorer() {
    HybridQuery hybridTfidf =
        HybridQuery.builder()
            .text("shoes support")
            .textFieldName("brief_description")
            .vector(new float[] {0.12f, 0.18f, 0.12f})
            .vectorFieldName("text_embedding")
            .textScorer("TFIDF")
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> results = index.query(hybridTfidf);
    assertThat(results).isNotEmpty();
  }

  /** Notebook cell: gerdav306fh — AggregateHybridQuery with TFIDF scorer */
  @Test
  @Order(15)
  @DisplayName("AggregateHybridQuery with TFIDF scorer")
  void testAggregateHybridQueryTFIDFScorer() {
    AggregateHybridQuery aggTfidf =
        AggregateHybridQuery.builder()
            .text("shoes support")
            .textFieldName("brief_description")
            .vector(new float[] {0.12f, 0.18f, 0.12f})
            .vectorFieldName("text_embedding")
            .textScorer("TFIDF")
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(aggTfidf);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(doc -> doc.containsKey("hybrid_score"));
  }

  // ===== Section 3: MultiVectorQuery =====

  /** Notebook cell: multi-vector-query-basic */
  @Test
  @Order(16)
  @DisplayName("MultiVectorQuery basic with two vector fields")
  void testMultiVectorQueryBasic() {
    com.redis.vl.query.Vector textVector =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.7)
            .build();

    com.redis.vl.query.Vector imageVector =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.8f, 0.1f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.3)
            .build();

    MultiVectorQuery multiVectorQuery =
        MultiVectorQuery.builder()
            .vectors(textVector, imageVector)
            .returnFields(Arrays.asList("product_id", "brief_description", "category"))
            .numResults(5)
            .build();

    List<Map<String, Object>> multiResults = index.query(multiVectorQuery);

    assertThat(multiResults).isNotEmpty();
    assertThat(multiResults).allMatch(doc -> doc.containsKey("combined_score"));
    assertThat(multiResults).allMatch(doc -> doc.containsKey("score_0"));
    assertThat(multiResults).allMatch(doc -> doc.containsKey("score_1"));
  }

  /** Notebook cell: multi-vector-query-weights */
  @Test
  @Order(17)
  @DisplayName("MultiVectorQuery with emphasis on image similarity")
  void testMultiVectorQueryImageHeavy() {
    com.redis.vl.query.Vector textVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.9f, 0.1f, 0.05f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.2)
            .build();

    com.redis.vl.query.Vector imageVec =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.9f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.8)
            .build();

    MultiVectorQuery imageHeavyQuery =
        MultiVectorQuery.builder()
            .vectors(textVec, imageVec)
            .returnFields(Arrays.asList("product_id", "brief_description", "category"))
            .numResults(3)
            .build();

    List<Map<String, Object>> results = index.query(imageHeavyQuery);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(doc -> doc.containsKey("combined_score"));
  }

  /** Notebook cell: multi-vector-query-filter */
  @Test
  @Order(18)
  @DisplayName("MultiVectorQuery with category filter")
  void testMultiVectorQueryWithFilter() {
    com.redis.vl.query.Vector textVecFilter =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .fieldName("text_embedding")
            .dtype("float32")
            .weight(0.6)
            .build();

    com.redis.vl.query.Vector imageVecFilter =
        com.redis.vl.query.Vector.builder()
            .vector(new float[] {0.8f, 0.1f})
            .fieldName("image_embedding")
            .dtype("float32")
            .weight(0.4)
            .build();

    MultiVectorQuery filteredMultiQuery =
        MultiVectorQuery.builder()
            .vectors(textVecFilter, imageVecFilter)
            .filterExpression(Filter.tag("category", "footwear"))
            .returnFields(Arrays.asList("product_id", "brief_description", "category", "price"))
            .numResults(5)
            .build();

    List<Map<String, Object>> results = index.query(filteredMultiQuery);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(doc -> doc.containsKey("combined_score"));
  }

  // ===== Section 4: Comparing Query Types =====

  /** Notebook cell: compare-queries */
  @Test
  @Order(19)
  @DisplayName("Compare all query types side by side")
  void testCompareQueryTypes() {
    // TextQuery
    TextQuery textQ =
        TextQuery.builder()
            .text("shoes")
            .textField("brief_description")
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .build();

    List<Map<String, Object>> textResults = index.query(textQ);
    assertThat(textResults).isNotEmpty();

    // HybridQuery (FT.HYBRID)
    HybridQuery hybridQ =
        HybridQuery.builder()
            .text("shoes")
            .textFieldName("brief_description")
            .vector(new float[] {0.1f, 0.2f, 0.1f})
            .vectorFieldName("text_embedding")
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
            .yieldTextScoreAs("text_score")
            .yieldVsimScoreAs("vector_similarity")
            .yieldCombinedScoreAs("hybrid_score")
            .build();

    List<Map<String, Object>> hybridResults = index.query(hybridQ);
    assertThat(hybridResults).isNotEmpty();

    // MultiVectorQuery
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

    MultiVectorQuery multiQ =
        MultiVectorQuery.builder()
            .vectors(mvText, mvImage)
            .returnFields(Arrays.asList("product_id", "brief_description"))
            .numResults(3)
            .build();

    List<Map<String, Object>> multiResults = index.query(multiQ);
    assertThat(multiResults).isNotEmpty();

    // All three query types should produce results
    assertThat(textResults).hasSizeGreaterThan(0);
    assertThat(hybridResults).hasSizeGreaterThan(0);
    assertThat(multiResults).hasSizeGreaterThan(0);
  }

  // ===== Section 5: Cleanup =====

  /** Notebook cell: cleanup-code — verified via tearDown */
  @Test
  @Order(20)
  @DisplayName("Cleanup - delete index and verify")
  void testCleanup() {
    assertThat(index.exists()).isTrue();
    index.delete(true);
    assertThat(index.exists()).isFalse();
    index = null; // prevent tearDown from double-deleting
  }
}
