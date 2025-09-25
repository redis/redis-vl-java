package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.exceptions.RedisVLException;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.BaseField;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import java.io.File;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.SearchResult;

/** Integration tests for SearchIndex */
@DisplayName("SearchIndex Integration Tests")
class SearchIndexIntegrationTest extends BaseIntegrationTest {

  private static final String TEST_PREFIX = "test_" + UUID.randomUUID().toString().substring(0, 8);
  private IndexSchema schema;
  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Create test schema matching Python test
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "my_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "rvl_" + TEST_PREFIX);
    schemaDict.put("index", indexConfig);

    // Fields matching Python test
    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of("name", "test", "type", "tag"),
            Map.of("name", "test_text", "type", "text"),
            Map.of(
                "name",
                "test_text_attrs",
                "type",
                "text",
                "attrs",
                Map.of("no_stem", true, "sortable", true)),
            Map.of("name", "test_tag", "type", "tag", "attrs", Map.of("case_sensitive", true)),
            Map.of("name", "test_numeric", "type", "numeric"),
            Map.of(
                "name",
                "test_numeric_attrs",
                "type",
                "numeric",
                "attrs",
                Map.of("sortable", true)));
    schemaDict.put("fields", fields);

    schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, unifiedJedis);
  }

  @Test
  @DisplayName("Should load data with automatic ULID generation")
  void testLoadDataWithAutoULID() {
    index.create(true, true);

    // Create test data without ID field
    List<Map<String, Object>> data =
        Arrays.asList(
            Map.of("test", "foo", "value", 100),
            Map.of("test", "bar", "value", 200),
            Map.of("test", "baz", "value", 300));

    // Load data with automatic ULID generation
    List<String> keys = index.load(data);

    // Verify keys were generated
    assertThat(keys).hasSize(3);

    // Verify all keys have the correct prefix
    for (String key : keys) {
      assertThat(key).startsWith(index.getPrefix() + ":");
    }

    // Verify documents can be fetched
    for (String key : keys) {
      Map<String, Object> doc = index.fetch(key);
      assertThat(doc).isNotNull();
      assertThat(doc).containsKey("test");
      assertThat(doc).containsKey("value");
    }

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index properties")
  void testSearchIndexProperties() {
    // Verify schema
    assertThat(index.getSchema()).isEqualTo(schema);

    // Custom settings
    assertThat(index.getName()).isEqualTo(schema.getIndex().getName());
    assertThat(index.getName()).startsWith("my_index_");

    // Default settings
    assertThat(index.getPrefix()).isEqualTo(schema.getIndex().getPrefix());
    assertThat(index.getPrefix()).startsWith("rvl_");
    assertThat(index.getKeySeparator()).isEqualTo(":");
    assertThat(index.getStorageType()).isEqualTo(IndexSchema.StorageType.HASH);
    assertThat(index.key("foo")).startsWith(index.getPrefix());
  }

  @Test
  @DisplayName("Test search index from YAML")
  void testSearchIndexFromYaml() {
    // Create YAML content matching Python test
    String yamlContent =
        String.format(
            """
        index:
          name: json-test_%s
          prefix: json_%s
          storage_type: json
        fields:
          - name: test_field
            type: text
        """,
            TEST_PREFIX, TEST_PREFIX);

    // Write to temp file
    try {
      File tempFile = File.createTempFile("test_schema", ".yaml");
      tempFile.deleteOnExit();
      java.nio.file.Files.writeString(tempFile.toPath(), yamlContent);

      SearchIndex yamlIndex = SearchIndex.fromYaml(tempFile.getAbsolutePath());

      assertThat(yamlIndex.getName()).startsWith("json-test_");
      assertThat(yamlIndex.getPrefix()).startsWith("json_");
      assertThat(yamlIndex.getKeySeparator()).isEqualTo(":");
      assertThat(yamlIndex.getStorageType()).isEqualTo(IndexSchema.StorageType.JSON);
      assertThat(yamlIndex.key("foo")).startsWith(yamlIndex.getPrefix());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("Test search index from dict")
  void testSearchIndexFromDict() {
    Map<String, Object> dictSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "dict_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "dict_" + TEST_PREFIX);
    dictSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of("name", "field1", "type", "text"), Map.of("name", "field2", "type", "tag"));
    dictSchema.put("fields", fields);

    SearchIndex dictIndex = SearchIndex.fromDict(dictSchema);

    assertThat(dictIndex.getName()).startsWith("dict_index_");
    assertThat(dictIndex.getPrefix()).startsWith("dict_");
    assertThat(dictIndex.getKeySeparator()).isEqualTo(":");
    assertThat(dictIndex.getStorageType()).isEqualTo(IndexSchema.StorageType.HASH);
    assertThat(dictIndex.getSchema().getFields()).hasSize(2);
    assertThat(dictIndex.key("foo")).startsWith(dictIndex.getPrefix());
  }

  @Test
  @DisplayName("Test search index from existing")
  void testSearchIndexFromExisting() {
    // Create the index first
    index.create(true);

    try {
      // Load from existing
      SearchIndex index2 = SearchIndex.fromExisting(index.getName(), unifiedJedis);

      // Verify index name matches
      assertThat(index2.getName()).isEqualTo(index.getName());

      // Verify storage type matches
      assertThat(index2.getStorageType()).isEqualTo(index.getStorageType());

      // Verify fields were reconstructed
      assertThat(index2.getSchema()).isNotNull();
      assertThat(index2.getSchema().getFields()).isNotEmpty();
      assertThat(index2.getSchema().getFields()).hasSameSizeAs(index.getSchema().getFields());

      // Verify we can query with the reconstructed index
      List<Map<String, Object>> data =
          List.of(Map.of("name", "test", "age", 25, "embedding", new float[] {0.1f, 0.2f, 0.3f}));
      List<String> keys = index2.load(data);
      assertThat(keys).hasSize(1);

      // Clean up
      index2.dropKeys(keys);
    } finally {
      index.delete(true);
    }
  }

  @Test
  @DisplayName("Test search index from existing complex")
  void testSearchIndexFromExistingComplex() {
    // Create complex schema matching Python test
    Map<String, Object> complexSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_complex_" + TEST_PREFIX);
    indexConfig.put("prefix", "test_" + TEST_PREFIX);
    indexConfig.put("storage_type", "json");
    complexSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of("name", "user", "type", "tag", "path", "$.user"),
            Map.of("name", "credit_score", "type", "tag", "path", "$.metadata.credit_score"),
            Map.of("name", "job", "type", "text", "path", "$.metadata.job"),
            Map.of(
                "name",
                "age",
                "type",
                "numeric",
                "path",
                "$.metadata.age",
                "attrs",
                Map.of("sortable", true)),
            Map.of(
                "name",
                "user_embedding",
                "type",
                "vector",
                "path",
                "$.user_embedding",
                "attrs",
                Map.of(
                    "dims",
                    3,
                    "distance_metric",
                    "cosine",
                    "algorithm",
                    "flat",
                    "datatype",
                    "float32")));
    complexSchema.put("fields", fields);

    SearchIndex complexIndex = SearchIndex.fromDict(complexSchema, unifiedJedis);
    complexIndex.create(true);

    try {
      SearchIndex index2 = SearchIndex.fromExisting(complexIndex.getName(), unifiedJedis);

      // Verify index name and storage type
      assertThat(index2.getName()).isEqualTo(complexIndex.getName());
      assertThat(index2.getStorageType()).isEqualTo(IndexSchema.StorageType.JSON);

      // Verify fields were reconstructed
      assertThat(index2.getSchema()).isNotNull();
      assertThat(index2.getSchema().getFields()).isNotEmpty();
      assertThat(index2.getSchema().getFields())
          .hasSameSizeAs(complexIndex.getSchema().getFields());

      // Verify specific field types were reconstructed correctly
      // For JSON storage, field names might be different (e.g., with JSON paths)
      // Let's find the vector field by checking all fields
      VectorField vectorField = null;
      for (BaseField field : index2.getSchema().getFields()) {
        if (field instanceof VectorField) {
          vectorField = (VectorField) field;
          break;
        }
      }

      assertThat(vectorField).isNotNull();
      assertThat(vectorField.getDimensions()).isEqualTo(3);
      assertThat(vectorField.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.COSINE);
    } finally {
      complexIndex.delete(true);
    }
  }

  @Test
  @DisplayName("Test search index no prefix")
  void testSearchIndexNoPrefix() {
    // Create new schema with empty prefix
    Map<String, Object> noPrefixSchemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "no_prefix_index_" + TEST_PREFIX);
    indexConfig.put("prefix", ""); // Empty prefix
    noPrefixSchemaDict.put("index", indexConfig);

    // Add minimal field
    List<Map<String, Object>> fields =
        Collections.singletonList(Map.of("name", "test", "type", "tag"));
    noPrefixSchemaDict.put("fields", fields);

    IndexSchema noPrefixSchema = IndexSchema.fromDict(noPrefixSchemaDict);
    SearchIndex noPrefixIndex = new SearchIndex(noPrefixSchema);

    assertThat(noPrefixIndex.getPrefix()).isEmpty();
    assertThat(noPrefixIndex.key("foo")).isEqualTo("foo");
  }

  @Test
  @DisplayName("Test search index create")
  void testSearchIndexCreate() {
    index.create(true, true);
    assertThat(index.exists()).isTrue();

    // Verify index exists using exists() method
    assertThat(index.exists()).isTrue();

    // Additional verification: try to get index info
    Map<String, Object> info = index.info();
    assertThat(info).isNotNull();
    assertThat(info).containsKey("index_name");
    assertThat(info.get("index_name")).isEqualTo(index.getName());

    // Verify index is in FT._LIST
    Set<String> indexList = unifiedJedis.ftList();
    assertThat(indexList).contains(index.getName());

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index delete")
  void testSearchIndexDelete() {
    index.create(true, true);
    index.delete(true);

    assertThat(index.exists()).isFalse();

    // Verify index is not in FT._LIST
    Set<String> indexList = unifiedJedis.ftList();
    assertThat(indexList).doesNotContain(index.getName());
  }

  @Test
  @DisplayName("Test list all indexes")
  void testListIndexes() {
    // Create multiple indexes
    index.create(true, true);

    // Create another index with different name
    Map<String, Object> schema2Dict = new HashMap<>();
    Map<String, Object> indexConfig2 = new HashMap<>();
    indexConfig2.put("name", "test_list_" + TEST_PREFIX);
    indexConfig2.put("prefix", "test2_" + TEST_PREFIX);
    schema2Dict.put("index", indexConfig2);

    List<Map<String, Object>> fields =
        Collections.singletonList(Map.of("name", "field1", "type", "tag"));
    schema2Dict.put("fields", fields);

    SearchIndex index2 = SearchIndex.fromDict(schema2Dict, unifiedJedis);
    index2.create(true, true);

    try {
      // List all indexes
      Set<String> allIndexes = index.listIndexes();

      // Verify both indexes are in the list
      assertThat(allIndexes).contains(index.getName());
      assertThat(allIndexes).contains(index2.getName());

      // Also verify using ftList directly
      Set<String> directList = unifiedJedis.ftList();
      assertThat(directList).isEqualTo(allIndexes);
    } finally {
      // Clean up both indexes
      index.delete(true);
      index2.delete(true);
    }
  }

  @Test
  @DisplayName("Test search index clear")
  void testSearchIndexClear() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data = Collections.singletonList(Map.of("id", "1", "test", "foo"));
    index.load(data, "id");

    // Clear the index
    int count = index.clear();
    assertThat(count).isEqualTo(data.size());
    assertThat(index.exists()).isTrue();

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index drop key")
  void testSearchIndexDropKey() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    List<String> keys = index.load(data, "id");

    // Drop single key
    int dropped = index.dropKeys(keys.get(0));
    assertThat(dropped).isEqualTo(1);
    assertThat(index.fetch(keys.get(0))).isNull();
    assertThat(index.fetch(keys.get(1))).isNotNull();

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index drop keys")
  void testSearchIndexDropKeys() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(
            Map.of("id", "1", "test", "foo"),
            Map.of("id", "2", "test", "bar"),
            Map.of("id", "3", "test", "baz"));
    List<String> keys = index.load(data, "id");

    // Drop multiple keys
    int dropped = index.dropKeys(keys.subList(0, 2));
    assertThat(dropped).isEqualTo(2);
    assertThat(index.fetch(keys.get(0))).isNull();
    assertThat(index.fetch(keys.get(1))).isNull();
    assertThat(index.fetch(keys.get(2))).isNotNull();

    assertThat(index.exists()).isTrue();

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index load and fetch")
  void testSearchIndexLoadAndFetch() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data = Collections.singletonList(Map.of("id", "1", "test", "foo"));
    index.load(data, "id");

    // Fetch the document
    Map<String, Object> result = index.fetch("1");
    assertThat(result).isNotNull();
    assertThat(result.get("test")).isEqualTo("foo");

    // Verify Redis storage
    String key = index.getPrefix() + index.getKeySeparator() + "1";
    String storedValue = jedis.hget(key, "test");
    assertThat(storedValue).isEqualTo("foo");

    // Delete index and verify fetch returns null
    index.delete(true);
    assertThat(index.exists()).isFalse();
    assertThat(index.fetch("1")).isNull();
  }

  @Test
  @DisplayName("Test search index load with preprocess")
  void testSearchIndexLoadPreprocess() {
    index.create(true, true);

    // Load test data with preprocessing
    List<Map<String, Object>> data = Collections.singletonList(Map.of("id", "1", "test", "foo"));

    // Preprocess function that changes "foo" to "bar"
    index.load(
        data,
        "id",
        record -> {
          Map<String, Object> processed = new HashMap<>(record);
          processed.put("test", "bar");
          return processed;
        });

    // Fetch and verify preprocessing was applied
    Map<String, Object> result = index.fetch("1");
    assertThat(result).isNotNull();
    assertThat(result.get("test")).isEqualTo("bar");

    // Verify Redis storage
    String key = index.getPrefix() + index.getKeySeparator() + "1";
    String storedValue = jedis.hget(key, "test");
    assertThat(storedValue).isEqualTo("bar");

    // Test bad preprocess function
    assertThatThrownBy(() -> index.load(data, "id", record -> null))
        .isInstanceOf(RedisVLException.class);

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test no id field")
  void testNoIdField() {
    index.create(true, true);

    List<Map<String, Object>> badData =
        Collections.singletonList(Map.of("wrong_key", "1", "value", "test"));

    // Should throw exception for missing id field
    assertThatThrownBy(() -> index.load(badData, "key"))
        .isInstanceOf(RedisVLException.class)
        .hasMessageContaining("Missing id field");

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test check index exists before delete")
  void testCheckIndexExistsBeforeDelete() {
    index.create(true, true);
    index.delete(true);

    // Should throw exception when trying to delete non-existent index
    assertThatThrownBy(() -> index.delete(false))
        .isInstanceOf(RedisVLException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  @DisplayName("Test check index exists before search")
  void testCheckIndexExistsBeforeSearch() {
    index.create(true, true);
    index.delete(true);

    // Create a vector query
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .numResults(7)
            .build();

    // Should throw exception when searching non-existent index
    assertThatThrownBy(() -> index.search(query))
        .isInstanceOf(RedisVLException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  @DisplayName("Test check index exists before info")
  void testCheckIndexExistsBeforeInfo() {
    index.create(true, true);
    index.delete(true);

    // Should throw exception when getting info for non-existent index
    assertThatThrownBy(() -> index.info())
        .isInstanceOf(RedisVLException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  @DisplayName("Test batch search")
  void testBatchSearch() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    index.load(data, "id");

    // Batch search with raw queries
    List<String> queries = Arrays.asList("@test:{foo}", "@test:{bar}");

    List<SearchResult> results = index.batchSearch(queries);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getTotalResults()).isEqualTo(1);
    assertThat(results.get(0).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":1");
    assertThat(results.get(1).getTotalResults()).isEqualTo(1);
    assertThat(results.get(1).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":2");

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test batch search with multiple batches")
  void testBatchSearchWithMultipleBatches() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    index.load(data, "id");

    // Batch search with 6 queries, batch size 2
    List<String> queries =
        Arrays.asList(
            "@test:{foo}",
            "@test:{bar}",
            "@test:{baz}", // Will have 0 results
            "@test:{foo}",
            "@test:{bar}",
            "@test:{baz}" // Will have 0 results
            );

    List<SearchResult> results = index.batchSearch(queries, 2);
    assertThat(results).hasSize(6);

    // First result for foo
    assertThat(results.get(0).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":1");

    // Second result for bar
    assertThat(results.get(1).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":2");

    // Third query should have zero results
    assertThat(results.get(2).getTotalResults()).isEqualTo(0);

    // Pattern repeats
    assertThat(results.get(3).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":1");
    assertThat(results.get(4).getDocuments().get(0).getId()).isEqualTo(index.getPrefix() + ":2");
    assertThat(results.get(5).getTotalResults()).isEqualTo(0);

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test batch query")
  void testBatchQuery() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    index.load(data, "id");

    // Create FilterQuery
    Filter query = Filter.tag("test", "foo");

    List<List<Map<String, Object>>> results = index.batchQuery(Collections.singletonList(query));

    assertThat(results).hasSize(1);
    assertThat(results.get(0)).hasSize(1);
    assertThat(results.get(0).get(0).get("id")).isEqualTo("1");

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test batch query with multiple batches")
  void testBatchQueryWithMultipleBatches() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    index.load(data, "id");

    // Create multiple FilterQueries
    List<Filter> queries = Arrays.asList(Filter.tag("test", "foo"), Filter.tag("test", "bar"));

    List<List<Map<String, Object>>> results = index.batchQuery(queries, 1);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).get(0).get("id")).isEqualTo("1");
    assertThat(results.get(1).get(0).get("id")).isEqualTo("2");

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index expire keys")
  void testSearchIndexExpireKeys() {
    index.create(true, true);

    // Load test data
    List<Map<String, Object>> data =
        Arrays.asList(Map.of("id", "1", "test", "foo"), Map.of("id", "2", "test", "bar"));
    List<String> keys = index.load(data, "id");

    // Set expiration on single key
    index.expireKeys(keys.get(0), 60);
    long ttl = jedis.ttl(keys.get(0));
    assertThat(ttl).isGreaterThan(0);
    assertThat(ttl).isLessThanOrEqualTo(60);

    // Test no expiration on the other key
    ttl = jedis.ttl(keys.get(1));
    assertThat(ttl).isEqualTo(-1); // -1 means no expiration

    // Set expiration on multiple keys
    List<Long> results = index.expireKeys(keys, 30);
    assertThat(results).hasSize(2);
    assertThat(results).allMatch(r -> r == 1L); // All operations should return 1 (success)

    // Verify TTLs are set
    for (String key : keys) {
      ttl = jedis.ttl(key);
      assertThat(ttl).isGreaterThan(0);
      assertThat(ttl).isLessThanOrEqualTo(30);
    }

    // Clean up
    index.delete(true);
  }

  @Test
  @DisplayName("Test search index validates query with flat algorithm")
  void testSearchIndexValidatesQueryWithFlatAlgorithm() {
    // Create index with FLAT vector field
    Map<String, Object> flatSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "flat_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "flat_" + TEST_PREFIX);
    flatSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Collections.singletonList(
            Map.of(
                "name",
                "user_embedding",
                "type",
                "vector",
                "attrs",
                Map.of(
                    "dims",
                    3,
                    "distance_metric",
                    "cosine",
                    "algorithm",
                    "flat",
                    "datatype",
                    "float32")));
    flatSchema.put("fields", fields);

    SearchIndex flatIndex = SearchIndex.fromDict(flatSchema, unifiedJedis);
    flatIndex.create(true);

    try {
      // Verify algorithm is FLAT
      VectorField vectorField = (VectorField) flatIndex.getSchema().getField("user_embedding");
      assertThat(vectorField.getAlgorithm()).isEqualTo(VectorField.Algorithm.FLAT);

      // Create query with ef_runtime (only valid for HNSW)
      VectorQuery query =
          VectorQuery.builder()
              .vector(new float[] {0.1f, 0.1f, 0.5f})
              .field("user_embedding")
              .returnFields("user", "credit_score", "age", "job", "location")
              .numResults(7)
              .efRuntime(100)
              .build();

      // Should throw validation error
      assertThatThrownBy(() -> flatIndex.query(query))
          .isInstanceOf(RedisVLException.class)
          .hasMessageContaining("EF_RUNTIME");
    } finally {
      flatIndex.delete(true);
    }
  }

  @Test
  @DisplayName("Test search index validates query with hnsw algorithm")
  void testSearchIndexValidatesQueryWithHnswAlgorithm() {
    // Create index with HNSW vector field
    Map<String, Object> hnswSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "hnsw_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "hnsw_" + TEST_PREFIX);
    hnswSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Collections.singletonList(
            Map.of(
                "name",
                "user_embedding",
                "type",
                "vector",
                "attrs",
                Map.of(
                    "dims",
                    3,
                    "distance_metric",
                    "cosine",
                    "algorithm",
                    "hnsw",
                    "datatype",
                    "float32")));
    hnswSchema.put("fields", fields);

    SearchIndex hnswIndex = SearchIndex.fromDict(hnswSchema, unifiedJedis);
    hnswIndex.create(true);

    try {
      // Load sample data
      List<Map<String, Object>> data =
          Collections.singletonList(
              Map.of("id", "1", "user_embedding", new float[] {0.1f, 0.2f, 0.3f}));
      hnswIndex.load(data, "id");

      // Verify algorithm is HNSW
      VectorField vectorField = (VectorField) hnswIndex.getSchema().getField("user_embedding");
      assertThat(vectorField.getAlgorithm()).isEqualTo(VectorField.Algorithm.HNSW);

      // Create query with ef_runtime (valid for HNSW)
      VectorQuery query =
          VectorQuery.builder()
              .vector(new float[] {0.1f, 0.1f, 0.5f})
              .field("user_embedding")
              .returnFields("user")
              .numResults(7)
              .efRuntime(100)
              .build();

      // Should not throw - ef_runtime is valid for HNSW
      List<Map<String, Object>> results = hnswIndex.query(query);
      assertThat(results).isNotNull();
    } finally {
      hnswIndex.delete(true);
    }
  }

  @Test
  @DisplayName("Test SearchIndex constructor with Redis URL string")
  void testSearchIndexConstructorWithRedisUrl() {
    // Create schema for this test
    IndexSchema schema =
        IndexSchema.builder()
            .name("url_constructor_" + TEST_PREFIX)
            .prefix("urlcon_" + TEST_PREFIX)
            .addTextField("content", textField -> {})
            .build();

    // Create index using Redis URL constructor
    SearchIndex urlIndex = new SearchIndex(schema, redisUrl);

    try {
      urlIndex.create(true);

      // Test that we can use the index
      Map<String, Object> doc = Map.of("id", "doc1", "content", "test content");
      String key = urlIndex.addDocument("doc1", doc);
      assertThat(key).isNotNull();

      // Verify the index exists
      assertThat(urlIndex.exists()).isTrue();
    } finally {
      if (urlIndex.exists()) {
        urlIndex.delete(true);
      }
    }
  }

  @Test
  @DisplayName("Test SearchIndex fromDict with Redis URL")
  void testSearchIndexFromDictWithRedisUrl() {
    // Create a unique schema for this test
    Map<String, Object> urlSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "url_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "url_" + TEST_PREFIX);
    urlSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "text_field", "type", "text"),
            Map.of(
                "name",
                "vector_field",
                "type",
                "vector",
                "attrs",
                Map.of("dims", 3, "distance_metric", "cosine", "algorithm", "flat")));
    urlSchema.put("fields", fields);

    // Create index using Redis URL from the test container
    SearchIndex urlIndex = SearchIndex.fromDict(urlSchema, redisUrl);

    try {
      urlIndex.create(true);

      // Test that we can use the index
      List<Map<String, Object>> data =
          List.of(
              Map.of("text_field", "test text", "vector_field", new float[] {0.1f, 0.2f, 0.3f}));
      List<String> keys = urlIndex.load(data);
      assertThat(keys).hasSize(1);

      // Query to verify data was loaded
      VectorQuery query =
          VectorQuery.builder()
              .vector(new float[] {0.1f, 0.2f, 0.3f})
              .field("vector_field")
              .numResults(1)
              .build();
      List<Map<String, Object>> results = urlIndex.query(query);
      assertThat(results).hasSize(1);
    } finally {
      if (urlIndex.exists()) {
        urlIndex.delete(true);
      }
    }
  }

  @Test
  @DisplayName("Test SearchIndex with validateOnLoad option")
  void testSearchIndexWithValidateOnLoad() {
    // Create schema with strict field requirements
    Map<String, Object> validateSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "validate_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "validate_" + TEST_PREFIX);
    validateSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "required_text", "type", "text"),
            Map.of(
                "name",
                "required_vector",
                "type",
                "vector",
                "attrs",
                Map.of("dims", 3, "distance_metric", "cosine", "algorithm", "flat")));
    validateSchema.put("fields", fields);

    // Create index with validateOnLoad=true
    SearchIndex validateIndex = SearchIndex.fromDict(validateSchema, unifiedJedis, true);

    try {
      validateIndex.create(true);

      // Valid data - should load successfully
      List<Map<String, Object>> validData =
          List.of(
              Map.of(
                  "required_text",
                  "valid text",
                  "required_vector",
                  new float[] {0.1f, 0.2f, 0.3f}));
      List<String> keys = validateIndex.load(validData);
      assertThat(keys).hasSize(1);

      // Invalid data - missing required vector field - should throw exception
      List<Map<String, Object>> invalidData = List.of(Map.of("required_text", "missing vector"));

      assertThatThrownBy(() -> validateIndex.load(invalidData))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("required_vector");
    } finally {
      validateIndex.delete(true);
    }
  }

  @Test
  @DisplayName("Test fromDict with URL string and validateOnLoad")
  void testFromDictWithUrlAndValidateOnLoad() {
    Map<String, Object> dictSchema = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "fromdict_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "fromdict_" + TEST_PREFIX);
    dictSchema.put("index", indexConfig);

    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "text", "type", "text"),
            Map.of(
                "name",
                "embedding",
                "type",
                "vector",
                "attrs",
                Map.of("dims", 2, "distance_metric", "l2", "algorithm", "flat")));
    dictSchema.put("fields", fields);

    // Test fromDict with Redis URL and validateOnLoad
    SearchIndex indexWithUrl = SearchIndex.fromDict(dictSchema, redisUrl, true);

    try {
      indexWithUrl.create(true);

      // Verify validateOnLoad is set
      assertThat(indexWithUrl.isValidateOnLoad()).isTrue();

      // Test validation with invalid data
      List<Map<String, Object>> invalidData = List.of(Map.of("text", "test"));

      assertThatThrownBy(() -> indexWithUrl.load(invalidData))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("embedding");

      // Test with valid data
      List<Map<String, Object>> validData =
          List.of(Map.of("text", "test", "embedding", new float[] {1.0f, 2.0f}));
      List<String> keys = indexWithUrl.load(validData);
      assertThat(keys).hasSize(1);
    } finally {
      if (indexWithUrl.exists()) {
        indexWithUrl.delete(true);
      }
    }
  }
}
