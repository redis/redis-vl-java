package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.search.SearchResult;

/**
 * Integration tests for JSON storage mode to ensure proper handling of JSONPath field names in both
 * vector and filter queries.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("JSON Storage Integration Tests")
class JsonStorageIntegrationTest extends BaseIntegrationTest {

  private static final String TEST_PREFIX =
      "test_json_" + UUID.randomUUID().toString().substring(0, 8);
  private static SearchIndex jsonIndex;
  private static SearchIndex hashIndex;

  @BeforeAll
  static void setup() {

    // Create JSON index with various field types
    Map<String, Object> jsonSchema = createJsonSchema();
    jsonIndex = SearchIndex.fromDict(jsonSchema, unifiedJedis);
    jsonIndex.create(true);

    // Create Hash index for comparison
    Map<String, Object> hashSchema = createHashSchema();
    hashIndex = SearchIndex.fromDict(hashSchema, unifiedJedis);
    hashIndex.create(true);

    // Load test data
    loadTestData();
  }

  @AfterAll
  static void cleanup() {
    if (jsonIndex != null) {
      jsonIndex.delete(true);
    }
    if (hashIndex != null) {
      hashIndex.delete(true);
    }
    // Connection cleanup is handled by BaseIntegrationTest
  }

  private static Map<String, Object> createJsonSchema() {
    Map<String, Object> schema = new HashMap<>();

    // Index configuration
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "json_test_" + TEST_PREFIX);
    indexConfig.put("prefix", "json:" + TEST_PREFIX + ":");
    indexConfig.put("storage_type", "json");
    schema.put("index", indexConfig);

    // Field definitions with JSONPath
    List<Map<String, Object>> fields = new ArrayList<>();

    // Simple fields with JSONPath
    fields.add(Map.of("name", "title", "type", "text", "path", "$.title"));
    fields.add(Map.of("name", "category", "type", "tag", "path", "$.category"));
    fields.add(Map.of("name", "price", "type", "numeric", "path", "$.price"));

    // Nested fields using JSONPath
    fields.add(Map.of("name", "brand", "type", "tag", "path", "$.metadata.brand"));
    fields.add(Map.of("name", "year", "type", "numeric", "path", "$.metadata.year"));

    // Vector field with JSONPath
    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");

    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "embedding");
    vectorField.put("type", "vector");
    vectorField.put("path", "$.embedding");
    vectorField.put("attrs", vectorAttrs);
    fields.add(vectorField);

    schema.put("fields", fields);
    return schema;
  }

  private static Map<String, Object> createHashSchema() {
    Map<String, Object> schema = new HashMap<>();

    // Index configuration
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "hash_test_" + TEST_PREFIX);
    indexConfig.put("prefix", "hash:" + TEST_PREFIX + ":");
    indexConfig.put("storage_type", "hash");
    schema.put("index", indexConfig);

    // Field definitions (no JSONPath for hash)
    List<Map<String, Object>> fields = new ArrayList<>();

    fields.add(Map.of("name", "title", "type", "text"));
    fields.add(Map.of("name", "category", "type", "tag"));
    fields.add(Map.of("name", "price", "type", "numeric"));
    fields.add(Map.of("name", "brand", "type", "tag"));
    fields.add(Map.of("name", "year", "type", "numeric"));

    // Vector field
    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");

    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "embedding");
    vectorField.put("type", "vector");
    vectorField.put("attrs", vectorAttrs);
    fields.add(vectorField);

    schema.put("fields", fields);
    return schema;
  }

  private static void loadTestData() {
    List<Map<String, Object>> jsonData = new ArrayList<>();
    List<Map<String, Object>> hashData = new ArrayList<>();

    // Product 1
    Map<String, Object> product1 = new HashMap<>();
    product1.put("title", "Laptop Pro");
    product1.put("category", "electronics");
    product1.put("price", 1500.0);
    product1.put("embedding", new float[] {0.1f, 0.2f, 0.3f});

    Map<String, Object> metadata1 = new HashMap<>();
    metadata1.put("brand", "TechCorp");
    metadata1.put("year", 2024);
    product1.put("metadata", metadata1);

    jsonData.add(product1);

    // For hash, flatten the data
    Map<String, Object> hashProduct1 = new HashMap<>();
    hashProduct1.put("title", "Laptop Pro");
    hashProduct1.put("category", "electronics");
    hashProduct1.put("price", 1500.0);
    hashProduct1.put("brand", "TechCorp");
    hashProduct1.put("year", 2024);
    hashProduct1.put("embedding", new float[] {0.1f, 0.2f, 0.3f});
    hashData.add(hashProduct1);

    // Product 2
    Map<String, Object> product2 = new HashMap<>();
    product2.put("title", "Smartphone X");
    product2.put("category", "electronics");
    product2.put("price", 800.0);
    product2.put("embedding", new float[] {0.2f, 0.3f, 0.4f});

    Map<String, Object> metadata2 = new HashMap<>();
    metadata2.put("brand", "PhoneCorp");
    metadata2.put("year", 2023);
    product2.put("metadata", metadata2);

    jsonData.add(product2);

    // For hash
    Map<String, Object> hashProduct2 = new HashMap<>();
    hashProduct2.put("title", "Smartphone X");
    hashProduct2.put("category", "electronics");
    hashProduct2.put("price", 800.0);
    hashProduct2.put("brand", "PhoneCorp");
    hashProduct2.put("year", 2023);
    hashProduct2.put("embedding", new float[] {0.2f, 0.3f, 0.4f});
    hashData.add(hashProduct2);

    // Product 3
    Map<String, Object> product3 = new HashMap<>();
    product3.put("title", "Headphones");
    product3.put("category", "audio");
    product3.put("price", 200.0);
    product3.put("embedding", new float[] {0.3f, 0.4f, 0.5f});

    Map<String, Object> metadata3 = new HashMap<>();
    metadata3.put("brand", "AudioTech");
    metadata3.put("year", 2024);
    product3.put("metadata", metadata3);

    jsonData.add(product3);

    // For hash
    Map<String, Object> hashProduct3 = new HashMap<>();
    hashProduct3.put("title", "Headphones");
    hashProduct3.put("category", "audio");
    hashProduct3.put("price", 200.0);
    hashProduct3.put("brand", "AudioTech");
    hashProduct3.put("year", 2024);
    hashProduct3.put("embedding", new float[] {0.3f, 0.4f, 0.5f});
    hashData.add(hashProduct3);

    // Load data
    List<String> jsonKeys = jsonIndex.load(jsonData, "title");
    List<String> hashKeys = hashIndex.load(hashData, "title");

    assertThat(jsonKeys).hasSize(3);
    assertThat(hashKeys).hasSize(3);

    // Wait a bit for indexing to complete
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test documents are stored and retrievable")
  void testDocumentsStored() {
    // Check if we can fetch a document
    // The key format is prefix + separator + id, where separator is ":"
    Map<String, Object> doc = jsonIndex.fetch("json:" + TEST_PREFIX + "::Laptop Pro");
    assertThat(doc).as("Document should be retrievable").isNotNull();
    assertThat(doc.get("title")).isEqualTo("Laptop Pro");

    // Verify the vector field is present
    assertThat(doc).containsKey("embedding");
    assertThat(doc.get("embedding")).isNotNull();

    // Try a wildcard search to see if any documents are indexed
    SearchResult allDocs = jsonIndex.search("*");
    assertThat(allDocs.getTotalResults()).as("Should have documents indexed").isGreaterThan(0);
  }

  @Test
  @Order(2)
  @DisplayName("Test vector query with JSONPath field name returns results")
  void testJsonVectorQueryWithJsonPath() {
    // First verify we have documents indexed
    SearchResult allDocs = jsonIndex.search("*");
    assertThat(allDocs.getTotalResults()).isGreaterThan(0);

    // Check index info
    Map<String, Object> indexInfo = jsonIndex.getInfo();
    assertThat(indexInfo).isNotNull();

    // Fetch a document to see its structure
    String docId = "json:" + TEST_PREFIX + "::Laptop Pro";
    Map<String, Object> doc = jsonIndex.fetch(docId);
    assertThat(doc).isNotNull();

    // Query using JSONPath field name
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.15f, 0.25f, 0.35f})
            .field("$.embedding") // JSONPath field name
            .numResults(3)
            .returnFields("$.title", "$.price", "$.category")
            .build();

    List<Map<String, Object>> results = jsonIndex.query(query);

    // Should return results
    assertThat(results).as("Vector query should return results").isNotEmpty();
    assertThat(results).hasSize(3);

    // Verify fields are returned with JSONPath names
    Map<String, Object> first = results.get(0);
    assertThat(first).containsKey("$.title");
    assertThat(first).containsKey("$.price");
    assertThat(first).containsKey("$.category");
  }

  @Test
  @Order(3)
  @DisplayName("Test vector query with plain field name also works")
  void testJsonVectorQueryWithPlainFieldName() {
    // Query using plain field name (without JSONPath)
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.15f, 0.25f, 0.35f})
            .field("embedding") // Plain field name
            .numResults(3)
            .returnFields("$.title", "$.price")
            .build();

    List<Map<String, Object>> results = jsonIndex.query(query);

    // Should also return results (backward compatibility)
    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(3);
  }

  @Test
  @Order(4)
  @DisplayName("Test filter query with JSONPath numeric field")
  void testJsonFilterQueryNumeric() {
    // Filter using JSONPath field name
    Filter filter = Filter.numeric("$.price").between(100, 1000);

    SearchResult result = jsonIndex.search(filter.build());

    // Should return results without syntax error
    assertThat(result.getTotalResults()).as("No results returned from query").isGreaterThan(0);
    assertThat(result.getDocuments()).isNotEmpty();

    // Verify price is in range
    result
        .getDocuments()
        .forEach(
            doc -> {
              // For JSON storage, RediSearch returns the full JSON document under "$"
              Object jsonDoc = doc.get("$");
              if (jsonDoc instanceof String) {
                try {
                  // Parse the JSON document
                  com.fasterxml.jackson.databind.ObjectMapper mapper =
                      new com.fasterxml.jackson.databind.ObjectMapper();
                  @SuppressWarnings("unchecked")
                  Map<String, Object> docMap = mapper.readValue((String) jsonDoc, Map.class);
                  Object price = docMap.get("price");
                  assertThat(price).as("Price field not found in JSON document").isNotNull();
                  double priceValue = Double.parseDouble(price.toString());
                  assertThat(priceValue).isBetween(100.0, 1000.0);
                } catch (Exception e) {
                  throw new RuntimeException("Failed to parse JSON document", e);
                }
              } else {
                // Fallback to direct field access
                Object price = doc.get("$.price");
                if (price == null) {
                  price = doc.get("price");
                }
                assertThat(price).as("Price field not found in document").isNotNull();
                double priceValue = Double.parseDouble(price.toString());
                assertThat(priceValue).isBetween(100.0, 1000.0);
              }
            });
  }

  @Test
  @Order(5)
  @DisplayName("Test filter query with nested JSONPath field")
  void testJsonFilterQueryNested() {
    // Filter using nested JSONPath field name
    Filter filter = Filter.tag("$.metadata.brand", "TechCorp");

    SearchResult result = jsonIndex.search(filter.build());

    // Should return results without syntax error
    assertThat(result.getTotalResults()).isEqualTo(1);
    assertThat(result.getDocuments()).hasSize(1);

    // Verify the brand - use flexible field access
    var doc = result.getDocuments().get(0);
    Object brand = doc.get("$.metadata.brand");
    if (brand == null) brand = doc.get("metadata.brand");
    if (brand == null) {
      // For JSON storage, check the "$" field which contains the full JSON doc
      Object jsonDoc = doc.get("$");
      if (jsonDoc instanceof String) {
        // Parse and extract nested field
        try {
          com.fasterxml.jackson.databind.ObjectMapper mapper =
              new com.fasterxml.jackson.databind.ObjectMapper();
          @SuppressWarnings("unchecked")
          Map<String, Object> docMap = mapper.readValue((String) jsonDoc, Map.class);
          @SuppressWarnings("unchecked")
          Map<String, Object> metadata = (Map<String, Object>) docMap.get("metadata");
          if (metadata != null) {
            brand = metadata.get("brand");
          }
        } catch (Exception e) {
          // Fallback
        }
      }
    }
    assertThat(brand).isEqualTo("TechCorp");
  }

  @Test
  @Order(6)
  @DisplayName("Test combined vector and filter query with JSONPath")
  void testJsonVectorQueryWithFilter() {
    // Combine vector query with filter
    Filter filter = Filter.tag("$.category", "electronics");

    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.15f, 0.25f, 0.35f})
            .field("$.embedding")
            .numResults(10)
            .withPreFilter(filter.build())
            .returnFields("$.title", "$.category")
            .build();

    List<Map<String, Object>> results = jsonIndex.query(query);

    // Should return only electronics
    assertThat(results).isNotEmpty();
    results.forEach(result -> assertThat(result.get("$.category")).isEqualTo("electronics"));
  }

  @Test
  @Order(7)
  @DisplayName("Test text search with JSONPath field")
  void testJsonTextSearch() {
    // Text search using JSONPath field
    Filter filter = Filter.text("$.title", "Laptop");

    SearchResult result = jsonIndex.search(filter.build());

    // Should find the laptop
    assertThat(result.getTotalResults()).isGreaterThan(0);
    assertThat(result.getDocuments()).isNotEmpty();

    // Verify document structure
    assertThat(result.getDocuments()).isNotEmpty();
    for (var doc : result.getDocuments()) {
      assertThat(doc.getId()).isNotNull();
      assertThat(doc.getProperties()).isNotEmpty();
    }

    // Verify title contains Laptop - try different field access patterns
    boolean foundLaptop =
        result.getDocuments().stream()
            .anyMatch(
                doc -> {
                  // Try different ways to access the title field
                  Object title = doc.get("$.title");
                  if (title == null) title = doc.get("title");
                  if (title == null) {
                    // For JSON storage, check the "$" field which contains the full JSON doc
                    Object jsonDoc = doc.get("$");
                    if (jsonDoc instanceof String) {
                      return ((String) jsonDoc).contains("Laptop");
                    }
                  }
                  return title != null && title.toString().contains("Laptop");
                });
    assertThat(foundLaptop).isTrue();
  }

  @Test
  @Order(8)
  @DisplayName("Compare hash vs JSON vector query results")
  void testHashVsJsonVectorQuery() {
    float[] queryVector = new float[] {0.15f, 0.25f, 0.35f};

    // Hash query (plain field names)
    VectorQuery hashQuery =
        VectorQuery.builder()
            .vector(queryVector)
            .field("embedding")
            .numResults(3)
            .returnFields("title", "price")
            .build();

    List<Map<String, Object>> hashResults = hashIndex.query(hashQuery);

    // JSON query (JSONPath field names)
    VectorQuery jsonQuery =
        VectorQuery.builder()
            .vector(queryVector)
            .field("$.embedding")
            .numResults(3)
            .returnFields("$.title", "$.price")
            .build();

    List<Map<String, Object>> jsonResults = jsonIndex.query(jsonQuery);

    // Both should return same number of results
    assertThat(hashResults).hasSize(3);
    assertThat(jsonResults).hasSize(3);

    // Results should be present
    assertThat(hashResults).isNotEmpty();
    assertThat(jsonResults).isNotEmpty();
  }

  @Test
  @Order(9)
  @DisplayName("Test that VectorQuery escapes JSONPath field names correctly")
  void testVectorQueryEscapesJsonPath() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .field("$.embedding")
            .numResults(5)
            .build();

    String queryString = query.toQueryString();

    // Should escape JSONPath characters for RediSearch
    assertThat(queryString).contains("@\\$\\.embedding");
    assertThat(queryString).doesNotContain("@$.embedding");
  }

  @Test
  @Order(10)
  @DisplayName("Test complex filter combinations with JSONPath")
  void testComplexJsonFilters() {
    // Combine multiple filters with JSONPath
    Filter priceFilter = Filter.numeric("$.price").between(500, 2000);
    Filter categoryFilter = Filter.tag("$.category", "electronics");
    Filter yearFilter = Filter.numeric("$.metadata.year").gte(2023);

    Filter combined = Filter.and(priceFilter, categoryFilter, yearFilter);

    SearchResult result = jsonIndex.search(combined.build());

    // Should return results matching all criteria
    assertThat(result.getTotalResults()).isGreaterThan(0);

    result
        .getDocuments()
        .forEach(
            doc -> {
              // Use flexible field access pattern
              Object priceObj = doc.get("$.price");
              if (priceObj == null) priceObj = doc.get("price");
              if (priceObj == null) {
                // Try parsing from JSON document
                Object jsonDoc = doc.get("$");
                if (jsonDoc instanceof String) {
                  try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docMap = mapper.readValue((String) jsonDoc, Map.class);
                    priceObj = docMap.get("price");
                  } catch (Exception e) {
                    // Ignore
                  }
                }
              }

              if (priceObj != null) {
                double price = Double.parseDouble(priceObj.toString());
                assertThat(price).isBetween(500.0, 2000.0);
              }

              Object categoryObj = doc.get("$.category");
              if (categoryObj == null) categoryObj = doc.get("category");
              if (categoryObj == null) {
                // Try parsing from JSON document
                Object jsonDoc = doc.get("$");
                if (jsonDoc instanceof String) {
                  try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docMap = mapper.readValue((String) jsonDoc, Map.class);
                    categoryObj = docMap.get("category");
                  } catch (Exception e) {
                    // Ignore
                  }
                }
              }
              assertThat(categoryObj).isEqualTo("electronics");

              Object yearObj = doc.get("$.metadata.year");
              if (yearObj == null) yearObj = doc.get("metadata.year");
              if (yearObj != null) {
                int year = Integer.parseInt(yearObj.toString());
                assertThat(year).isGreaterThanOrEqualTo(2023);
              }
            });
  }

  @Test
  @Order(11)
  @DisplayName("Test fetch document from JSON index preserves structure")
  void testJsonFetchDocument() {
    // Load and get keys
    List<String> keys =
        jsonIndex.load(
            List.of(
                Map.of(
                    "title",
                    "Test Product",
                    "category",
                    "test",
                    "price",
                    100.0,
                    "embedding",
                    new float[] {0.5f, 0.5f, 0.5f},
                    "metadata",
                    Map.of("brand", "TestBrand", "year", 2024))),
            "title");

    assertThat(keys).hasSize(1);
    String key = keys.get(0);

    // Fetch the document
    Map<String, Object> doc = jsonIndex.fetch(key);

    // Should preserve nested structure
    assertThat(doc).containsKey("metadata");
    assertThat(doc.get("metadata")).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
    assertThat(metadata).containsEntry("brand", "TestBrand");

    // Handle numeric types - JSON may store as Double
    Object year = metadata.get("year");
    if (year instanceof Double) {
      assertThat(((Double) year).intValue()).isEqualTo(2024);
    } else {
      assertThat(year).isEqualTo(2024);
    }
  }
}
