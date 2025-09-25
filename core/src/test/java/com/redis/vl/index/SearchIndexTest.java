package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.schema.*;
import com.redis.vl.test.BaseIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.UnifiedJedis;

/** Integration tests for SearchIndex */
@DisplayName("SearchIndex Integration Tests")
class SearchIndexTest extends BaseIntegrationTest {

  private UnifiedJedis unifiedJedis;
  private SearchIndex searchIndex;
  private IndexSchema schema;
  private Jedis jedis;

  @BeforeEach
  void setUp() {
    // Create UnifiedJedis for RediSearch operations
    HostAndPort hostAndPort = new HostAndPort(REDIS.getHost(), REDIS.getRedisPort());
    unifiedJedis = new UnifiedJedis(hostAndPort);

    // Get a regular Jedis for verification
    jedis = getJedis();

    // Create a test schema
    schema =
        IndexSchema.builder()
            .name("test-index")
            .prefix("doc:")
            .storageType(IndexSchema.StorageType.HASH)
            .field(TextField.of("title").sortable().build())
            .field(TagField.of("category").build())
            .field(NumericField.of("price").sortable().build())
            .field(VectorField.of("embedding", 768).build())
            .build();

    searchIndex = new SearchIndex(schema, unifiedJedis);
  }

  @AfterEach
  void tearDown() {
    // Clean up any created indexes
    if (searchIndex != null && searchIndex.exists()) {
      searchIndex.drop();
    }

    // Close connections
    if (jedis != null) {
      jedis.close();
    }
    if (unifiedJedis != null) {
      unifiedJedis.close();
    }
  }

  @Test
  @DisplayName("Should create index from schema")
  void shouldCreateIndexFromSchema() {
    // When
    searchIndex.create();

    // Then - verify index exists
    assertThat(searchIndex.exists()).isTrue();
  }

  @Test
  @DisplayName("Should drop existing index")
  void shouldDropExistingIndex() {
    // Given
    searchIndex.create();

    // When
    boolean dropped = searchIndex.drop();

    // Then
    assertThat(dropped).isTrue();

    // Verify index doesn't exist
    assertThat(searchIndex.exists()).isFalse();
  }

  @Test
  @DisplayName("Should check if index exists")
  void shouldCheckIfIndexExists() {
    // Initially should not exist
    assertThat(searchIndex.exists()).isFalse();

    // After creation should exist
    searchIndex.create();
    assertThat(searchIndex.exists()).isTrue();

    // After dropping should not exist
    searchIndex.drop();
    assertThat(searchIndex.exists()).isFalse();
  }

  @Test
  @DisplayName("Should recreate index")
  void shouldRecreateIndex() {
    // Given
    searchIndex.create();

    // When
    searchIndex.recreate();

    // Then - index should still exist
    assertThat(searchIndex.exists()).isTrue();
  }

  @Test
  @DisplayName("Should add document to index")
  void shouldAddDocumentToIndex() {
    // Given
    searchIndex.create();

    Map<String, Object> document = new HashMap<>();
    document.put("title", "Redis in Action");
    document.put("category", "database,nosql");
    document.put("price", 39.99);
    document.put("embedding", new float[768]); // Simple zero vector for testing

    // When
    String docId = searchIndex.addDocument("doc:1", document);

    // Then
    assertThat(docId).isEqualTo("doc:1");

    // Verify document exists
    Map<String, String> doc = jedis.hgetAll("doc:1");
    assertThat(doc).containsKey("title");
    assertThat(doc.get("title")).isEqualTo("Redis in Action");
  }

  @Test
  @DisplayName("Should update existing document")
  void shouldUpdateExistingDocument() {
    // Given
    searchIndex.create();

    Map<String, Object> document = new HashMap<>();
    document.put("title", "Original Title");
    document.put("price", 29.99);
    searchIndex.addDocument("doc:1", document);

    // When
    Map<String, Object> updatedDoc = new HashMap<>();
    updatedDoc.put("title", "Updated Title");
    updatedDoc.put("price", 39.99);
    searchIndex.updateDocument("doc:1", updatedDoc);

    // Then
    Map<String, String> doc = jedis.hgetAll("doc:1");
    assertThat(doc.get("title")).isEqualTo("Updated Title");
    assertThat(doc.get("price")).isEqualTo("39.99");
  }

  @Test
  @DisplayName("Should delete document from index")
  void shouldDeleteDocumentFromIndex() {
    // Given
    searchIndex.create();

    Map<String, Object> document = new HashMap<>();
    document.put("title", "Test Document");
    searchIndex.addDocument("doc:1", document);

    // When
    boolean deleted = searchIndex.deleteDocument("doc:1");

    // Then
    assertThat(deleted).isTrue();

    // Verify document doesn't exist
    Map<String, String> doc = jedis.hgetAll("doc:1");
    assertThat(doc).isEmpty();
  }

  @Test
  @DisplayName("Should get document count")
  void shouldGetDocumentCount() {
    // Given
    searchIndex.create();

    // Initially should be 0
    assertThat(searchIndex.getDocumentCount()).isEqualTo(0);

    // Add documents
    Map<String, Object> doc1 = new HashMap<>();
    doc1.put("title", "Document 1");
    searchIndex.addDocument("doc:1", doc1);

    Map<String, Object> doc2 = new HashMap<>();
    doc2.put("title", "Document 2");
    searchIndex.addDocument("doc:2", doc2);

    // Then
    assertThat(searchIndex.getDocumentCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should get index info")
  void shouldGetIndexInfo() {
    // Given
    searchIndex.create();

    // When
    Map<String, Object> info = searchIndex.getInfo();

    // Then
    assertThat(info).containsKey("index_name");
    assertThat(info.get("index_name")).isEqualTo(schema.getName());
    // RediSearch returns "attributes" instead of "fields"
    assertThat(info).containsKey("attributes");
  }

  @Test
  @DisplayName("Should handle JSON storage type")
  void shouldHandleJsonStorageType() {
    // Given
    IndexSchema jsonSchema =
        IndexSchema.builder()
            .name("json-index")
            .prefix("json:")
            .storageType(IndexSchema.StorageType.JSON)
            .field(TextField.of("content").build())
            .build();

    SearchIndex jsonIndex = new SearchIndex(jsonSchema, unifiedJedis);
    jsonIndex.create();

    Map<String, Object> document = new HashMap<>();
    document.put("content", "JSON document content");

    // When
    String docId = jsonIndex.addDocument("json:1", document);

    // Then
    assertThat(docId).isEqualTo("json:1");

    // Cleanup
    jsonIndex.drop();
  }

  @Test
  @DisplayName("Should throw exception when fromDict receives null schema")
  @SuppressWarnings("DataFlowIssue")
  // Intentionally testing failure case
  void shouldThrowExceptionWhenFromDictReceivesNullSchema() {
    // When/Then
    assertThatThrownBy(() -> SearchIndex.fromDict(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Schema dictionary cannot be null");

    assertThatThrownBy(() -> SearchIndex.fromDict(null, unifiedJedis))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Schema dictionary cannot be null");
  }

  @Test
  @DisplayName("Should throw exception when fromDict receives null client")
  @SuppressWarnings("DataFlowIssue")
  // Intentionally testing failure case
  void shouldThrowExceptionWhenFromDictReceivesNullClient() {
    // Given
    var schema =
        Map.of(
            "index",
            Map.of("name", "test", "prefix", "test:"),
            "fields",
            List.of(Map.of("name", "title", "type", "text")));

    // When/Then
    assertThatThrownBy(() -> SearchIndex.fromDict(schema, (UnifiedJedis) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Redis client cannot be null");
  }

  @Test
  @DisplayName("Should work with immutable maps from Map.of()")
  void shouldWorkWithImmutableMaps() {
    // Given - using Map.of() creates immutable maps
    var schema =
        Map.of(
            "index",
            Map.of("name", "immutable-test", "prefix", "immut:", "storage_type", "hash"),
            "fields",
            List.of(
                Map.of("name", "title", "type", "text"),
                Map.of("name", "score", "type", "numeric")));

    // When
    SearchIndex index = SearchIndex.fromDict(schema, unifiedJedis);

    // Then
    assertThat(index).isNotNull();
    assertThat(index.getName()).isEqualTo("immutable-test");
    assertThat(index.getPrefix()).isEqualTo("immut:");

    // Cleanup
    if (index.exists()) {
      index.drop();
    }
  }
}
