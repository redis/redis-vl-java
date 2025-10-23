package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.search.SearchResult;

/**
 * Integration tests for UNF (un-normalized form) and NOINDEX field attributes (#374).
 *
 * <p>Tests field attributes for controlling sorting normalization and indexing behavior.
 *
 * <p>Python reference: PR #386 - UNF/NOINDEX support
 */
@Tag("integration")
@DisplayName("UNF/NOINDEX Field Attributes Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnfNoindexIntegrationTest extends BaseIntegrationTest {

  private static final String INDEX_NAME = "unf_noindex_test_idx";
  private static SearchIndex searchIndex;

  @BeforeAll
  static void setupIndex() {
    // Clean up any existing index
    try {
      unifiedJedis.ftDropIndex(INDEX_NAME);
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }

    // Create schema with UNF and NOINDEX fields
    IndexSchema schema =
        IndexSchema.builder()
            .name(INDEX_NAME)
            .prefix("product:")
            // Regular sortable text field (normalized)
            .field(TextField.builder().name("name").sortable(true).build())
            // UNF sortable text field (un-normalized, preserves case)
            .field(TextField.builder().name("brand").sortable(true).unf(true).build())
            // NOINDEX sortable text field (not searchable, but sortable)
            .field(TextField.builder().name("sku").sortable(true).indexed(false).build())
            // Regular sortable numeric field
            .field(NumericField.builder().name("price").sortable(true).build())
            // UNF numeric field (flag stored, but Jedis limitation)
            .field(NumericField.builder().name("stock").sortable(true).unf(true).build())
            // Regular indexed text field for search
            .field(TextField.builder().name("description").build())
            .build();

    searchIndex = new SearchIndex(schema, unifiedJedis);
    searchIndex.create();

    // Insert test documents with mixed case data
    Map<String, Object> doc1 = new HashMap<>();
    doc1.put("id", "1");
    doc1.put("name", "apple laptop");
    doc1.put("brand", "Apple");
    doc1.put("sku", "SKU-001");
    doc1.put("price", 1200);
    doc1.put("stock", 50);
    doc1.put("description", "Premium laptop");

    Map<String, Object> doc2 = new HashMap<>();
    doc2.put("id", "2");
    doc2.put("name", "banana phone");
    doc2.put("brand", "banana");
    doc2.put("sku", "SKU-002");
    doc2.put("price", 800);
    doc2.put("stock", 30);
    doc2.put("description", "Budget phone");

    Map<String, Object> doc3 = new HashMap<>();
    doc3.put("id", "3");
    doc3.put("name", "cherry tablet");
    doc3.put("brand", "CHERRY");
    doc3.put("sku", "SKU-003");
    doc3.put("price", 500);
    doc3.put("stock", 20);
    doc3.put("description", "Mid-range tablet");

    Map<String, Object> doc4 = new HashMap<>();
    doc4.put("id", "4");
    doc4.put("name", "date watch");
    doc4.put("brand", "Date");
    doc4.put("sku", "SKU-004");
    doc4.put("price", 300);
    doc4.put("stock", 10);
    doc4.put("description", "Smart watch");

    // Load all documents
    searchIndex.load(Arrays.asList(doc1, doc2, doc3, doc4), "id");

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
  @DisplayName("Should create fields with UNF attribute")
  void testUnfFieldCreation() {
    IndexSchema schema = searchIndex.getSchema();

    // TextField with UNF
    TextField brandField =
        (TextField)
            schema.getFields().stream().filter(f -> f.getName().equals("brand")).findFirst().get();
    assertThat(brandField.isUnf()).isTrue();
    assertThat(brandField.isSortable()).isTrue();

    // NumericField with UNF (flag stored despite Jedis limitation)
    NumericField stockField =
        (NumericField)
            schema.getFields().stream().filter(f -> f.getName().equals("stock")).findFirst().get();
    assertThat(stockField.isUnf()).isTrue();
    assertThat(stockField.isSortable()).isTrue();
  }

  @Test
  @Order(2)
  @DisplayName("Should sort by regular field with case normalization")
  void testRegularSortableCaseNormalization() {
    // Query sorted by 'name' (regular sortable, normalized)
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "*",
            redis.clients.jedis.search.FTSearchParams.searchParams()
                .sortBy("name", SortingOrder.ASC)
                .limit(0, 10));

    assertThat(result.getTotalResults()).isEqualTo(4);

    // With normalization, all names are treated lowercase
    // Expected order: "apple laptop" < "banana phone" < "cherry tablet" < "date watch"
    List<String> names = new ArrayList<>();
    result
        .getDocuments()
        .forEach(
            doc -> {
              names.add(doc.getString("name"));
            });

    assertThat(names)
        .containsExactly("apple laptop", "banana phone", "cherry tablet", "date watch");
  }

  @Test
  @Order(3)
  @DisplayName("Should sort by UNF field preserving original case")
  void testUnfSortablePreservesCase() {
    // Query sorted by 'brand' (UNF sortable, case-preserved)
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "*",
            redis.clients.jedis.search.FTSearchParams.searchParams()
                .sortBy("brand", SortingOrder.ASC)
                .limit(0, 10));

    assertThat(result.getTotalResults()).isEqualTo(4);

    // With UNF, case is preserved in sorting
    // Expected order: "Apple" < "CHERRY" < "Date" < "banana"
    // (uppercase letters sort before lowercase in ASCII)
    List<String> brands = new ArrayList<>();
    result
        .getDocuments()
        .forEach(
            doc -> {
              brands.add(doc.getString("brand"));
            });

    // ASCII order: 'A' (65) < 'C' (67) < 'D' (68) < 'b' (98)
    assertThat(brands).containsExactly("Apple", "CHERRY", "Date", "banana");
  }

  @Test
  @Order(4)
  @DisplayName("Should allow sorting by numeric field")
  void testNumericSortable() {
    // Query sorted by 'price' (regular numeric sortable)
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "*",
            redis.clients.jedis.search.FTSearchParams.searchParams()
                .sortBy("price", SortingOrder.ASC)
                .limit(0, 10));

    assertThat(result.getTotalResults()).isEqualTo(4);

    List<Double> prices = new ArrayList<>();
    result
        .getDocuments()
        .forEach(
            doc -> {
              prices.add(Double.parseDouble(doc.getString("price")));
            });

    assertThat(prices).containsExactly(300.0, 500.0, 800.0, 1200.0);
  }

  @Test
  @Order(5)
  @DisplayName("Should allow sorting by UNF numeric field (Jedis limitation noted)")
  void testUnfNumericSortable() {
    // Query sorted by 'stock' (UNF numeric sortable, but Jedis doesn't support sortableUNF for
    // numeric)
    // This test documents current behavior with Jedis limitation
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "*",
            redis.clients.jedis.search.FTSearchParams.searchParams()
                .sortBy("stock", SortingOrder.ASC)
                .limit(0, 10));

    assertThat(result.getTotalResults()).isEqualTo(4);

    List<Double> stock = new ArrayList<>();
    result
        .getDocuments()
        .forEach(
            doc -> {
              stock.add(Double.parseDouble(doc.getString("stock")));
            });

    // Even though UNF is set, numeric fields sort normally
    // (Jedis doesn't have sortableUNF() for NumericField yet)
    assertThat(stock).containsExactly(10.0, 20.0, 30.0, 50.0);
  }

  @Test
  @Order(6)
  @DisplayName("NOINDEX field should not be searchable but should be retrievable")
  void testNoindexFieldNotSearchable() {
    // Try to search by 'sku' field (NOINDEX, should not match)
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME, "@sku:SKU-001", redis.clients.jedis.search.FTSearchParams.searchParams());

    // NOINDEX field should not be searchable
    assertThat(result.getTotalResults()).isEqualTo(0);
  }

  @Test
  @Order(7)
  @DisplayName("NOINDEX field should be retrievable in results")
  void testNoindexFieldRetrievable() {
    // Search by indexed field, but retrieve NOINDEX field
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "@description:laptop",
            redis.clients.jedis.search.FTSearchParams.searchParams());

    assertThat(result.getTotalResults()).isEqualTo(1);

    // NOINDEX field should still be retrievable
    redis.clients.jedis.search.Document doc = result.getDocuments().get(0);
    assertThat(doc.getString("sku")).isEqualTo("SKU-001");
  }

  @Test
  @Order(8)
  @DisplayName("NOINDEX field should be sortable")
  void testNoindexFieldSortable() {
    // Query sorted by 'sku' (NOINDEX but sortable)
    SearchResult result =
        unifiedJedis.ftSearch(
            INDEX_NAME,
            "*",
            redis.clients.jedis.search.FTSearchParams.searchParams()
                .sortBy("sku", SortingOrder.ASC)
                .limit(0, 10));

    assertThat(result.getTotalResults()).isEqualTo(4);

    List<String> skus = new ArrayList<>();
    result
        .getDocuments()
        .forEach(
            doc -> {
              skus.add(doc.getString("sku"));
            });

    // Should sort correctly even though not indexed
    assertThat(skus).containsExactly("SKU-001", "SKU-002", "SKU-003", "SKU-004");
  }

  @Test
  @Order(9)
  @DisplayName("UNF should only apply when sortable is true")
  void testUnfRequiresSortable() {
    // Create a field with UNF but not sortable
    TextField field = TextField.builder().name("test").unf(true).build();

    // UNF flag is set but field is not sortable
    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isFalse();

    // When converted to Jedis field, UNF should be ignored (no sortable)
    redis.clients.jedis.search.schemafields.SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    // Field should not be sortable, so UNF has no effect
  }

  @Test
  @Order(10)
  @DisplayName("Should support combined UNF and NOINDEX attributes")
  void testUnfAndNoindexCombined() {
    // Clean up existing index
    try {
      unifiedJedis.ftDropIndex("combined_test_idx");
    } catch (Exception e) {
      // Ignore
    }

    // Create field with both UNF and NOINDEX
    IndexSchema schema =
        IndexSchema.builder()
            .name("combined_test_idx")
            .prefix("test:")
            .field(TextField.builder().name("code").sortable(true).unf(true).indexed(false).build())
            .field(TextField.builder().name("description").build())
            .build();

    SearchIndex testIndex = new SearchIndex(schema, unifiedJedis);
    testIndex.create();

    try {
      // Insert test data
      Map<String, Object> doc1 = new HashMap<>();
      doc1.put("id", "1");
      doc1.put("code", "Alpha");
      doc1.put("description", "First");

      Map<String, Object> doc2 = new HashMap<>();
      doc2.put("id", "2");
      doc2.put("code", "beta");
      doc2.put("description", "Second");

      // Load documents
      testIndex.load(Arrays.asList(doc1, doc2), "id");

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // Field should be sortable with UNF (case preserved)
      SearchResult result =
          unifiedJedis.ftSearch(
              "combined_test_idx",
              "*",
              redis.clients.jedis.search.FTSearchParams.searchParams()
                  .sortBy("code", SortingOrder.ASC));

      assertThat(result.getTotalResults()).isEqualTo(2);

      List<String> codes = new ArrayList<>();
      result.getDocuments().forEach(doc -> codes.add(doc.getString("code")));

      // UNF preserves case: 'A' (65) < 'b' (98)
      assertThat(codes).containsExactly("Alpha", "beta");

      // Field should not be searchable (NOINDEX)
      SearchResult searchResult =
          unifiedJedis.ftSearch(
              "combined_test_idx",
              "@code:Alpha",
              redis.clients.jedis.search.FTSearchParams.searchParams());
      assertThat(searchResult.getTotalResults()).isEqualTo(0);

    } finally {
      testIndex.drop();
    }
  }
}
