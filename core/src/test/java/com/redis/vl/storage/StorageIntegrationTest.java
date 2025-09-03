package com.redis.vl.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;

@DisplayName("Storage Integration Tests")
class StorageIntegrationTest extends BaseIntegrationTest {

  private UnifiedJedis jedis;
  private HashStorage hashStorage;
  private JsonStorage jsonStorage;
  private IndexSchema hashSchema;
  private IndexSchema jsonSchema;
  private static final String TEST_PREFIX = "storage_test";

  @BeforeEach
  void setUp() {
    jedis = unifiedJedis;

    // Create hash schema
    Map<String, Object> hashSchemaDict = new HashMap<>();
    Map<String, Object> hashIndexConfig = new HashMap<>();
    hashIndexConfig.put("name", "hash_storage_test");
    hashIndexConfig.put("prefix", TEST_PREFIX + "_hash");
    hashIndexConfig.put("storage_type", "hash");
    hashIndexConfig.put("key_separator", ":");
    hashSchemaDict.put("index", hashIndexConfig);

    List<Map<String, Object>> hashFields =
        Arrays.asList(
            Map.of("name", "name", "type", "text"),
            Map.of("name", "age", "type", "numeric"),
            Map.of("name", "tags", "type", "tag"),
            Map.of(
                "name", "embedding",
                "type", "vector",
                "attrs",
                    Map.of(
                        "dims", 3,
                        "distance_metric", "cosine",
                        "algorithm", "flat",
                        "datatype", "float32")));
    hashSchemaDict.put("fields", hashFields);
    hashSchema = IndexSchema.fromDict(hashSchemaDict);
    hashStorage = new HashStorage(hashSchema);

    // Create JSON schema
    Map<String, Object> jsonSchemaDict = new HashMap<>();
    Map<String, Object> jsonIndexConfig = new HashMap<>();
    jsonIndexConfig.put("name", "json_storage_test");
    jsonIndexConfig.put("prefix", TEST_PREFIX + "_json");
    jsonIndexConfig.put("storage_type", "json");
    jsonIndexConfig.put("key_separator", ":");
    jsonSchemaDict.put("index", jsonIndexConfig);

    List<Map<String, Object>> jsonFields =
        Arrays.asList(
            Map.of("name", "name", "type", "text", "path", "$.name"),
            Map.of("name", "age", "type", "numeric", "path", "$.age"),
            Map.of("name", "tags", "type", "tag", "path", "$.tags"),
            Map.of(
                "name", "embedding",
                "type", "vector",
                "path", "$.embedding",
                "attrs",
                    Map.of(
                        "dims", 3,
                        "distance_metric", "cosine",
                        "algorithm", "flat",
                        "datatype", "float32")));
    jsonSchemaDict.put("fields", jsonFields);
    jsonSchema = IndexSchema.fromDict(jsonSchemaDict);
    jsonStorage = new JsonStorage(jsonSchema);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    if (jedis != null) {
      jedis.keys(TEST_PREFIX + "*").forEach(jedis::del);
    }
  }

  @Test
  @DisplayName("Test basic write and get operations for HashStorage")
  void testHashStorageBasicOperations() {
    // Prepare test data
    List<Map<String, Object>> objects =
        Arrays.asList(
            Map.of(
                "name",
                "John Doe",
                "age",
                30,
                "tags",
                "engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "Jane Smith",
                "age",
                25,
                "tags",
                "designer",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}));

    // Test write operation
    List<String> keys = hashStorage.write(jedis, objects);
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).startsWith(TEST_PREFIX + "_hash:");
    assertThat(keys.get(1)).startsWith(TEST_PREFIX + "_hash:");

    // Test get operation
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys);
    assertThat(retrieved).hasSize(2);
    assertThat(retrieved.get(0).get("name")).isEqualTo("John Doe");
    assertThat(retrieved.get(0).get("age")).isEqualTo("30");
    assertThat(retrieved.get(1).get("name")).isEqualTo("Jane Smith");
    assertThat(retrieved.get(1).get("age")).isEqualTo("25");
  }

  @Test
  @DisplayName("Test basic write and get operations for JsonStorage")
  void testJsonStorageBasicOperations() {
    // Prepare test data
    List<Map<String, Object>> objects =
        Arrays.asList(
            Map.of(
                "name",
                "John Doe",
                "age",
                30,
                "tags",
                "engineer",
                "embedding",
                new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "name",
                "Jane Smith",
                "age",
                25,
                "tags",
                "designer",
                "embedding",
                new float[] {0.4f, 0.5f, 0.6f}));

    // Test write operation
    List<String> keys = jsonStorage.write(jedis, objects);
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).startsWith(TEST_PREFIX + "_json:");
    assertThat(keys.get(1)).startsWith(TEST_PREFIX + "_json:");

    // Test get operation
    List<Map<String, Object>> retrieved = jsonStorage.get(jedis, keys);
    assertThat(retrieved).hasSize(2);
    assertThat(retrieved.get(0).get("name")).isEqualTo("John Doe");
    // JSON stores numbers as Double
    assertThat(((Number) retrieved.get(0).get("age")).intValue()).isEqualTo(30);
    assertThat(retrieved.get(1).get("name")).isEqualTo("Jane Smith");
    assertThat(((Number) retrieved.get(1).get("age")).intValue()).isEqualTo(25);
  }

  @Test
  @DisplayName("Test write with custom keys")
  void testWriteWithCustomKeys() {
    List<Map<String, Object>> objects =
        Arrays.asList(Map.of("name", "John", "age", 30), Map.of("name", "Jane", "age", 25));

    List<String> customKeys =
        Arrays.asList(TEST_PREFIX + "_hash:custom1", TEST_PREFIX + "_hash:custom2");

    List<String> keys = hashStorage.write(jedis, objects, null, customKeys);
    assertThat(keys).isEqualTo(customKeys);

    // Verify data was stored with custom keys
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys);
    assertThat(retrieved.get(0).get("name")).isEqualTo("John");
    assertThat(retrieved.get(1).get("name")).isEqualTo("Jane");
  }

  @Test
  @DisplayName("Test write with id field")
  void testWriteWithIdField() {
    List<Map<String, Object>> objects =
        Arrays.asList(
            Map.of("id", "user1", "name", "John", "age", 30),
            Map.of("id", "user2", "name", "Jane", "age", 25));

    List<String> keys = hashStorage.write(jedis, objects, "id", null);
    assertThat(keys).containsExactly(TEST_PREFIX + "_hash:user1", TEST_PREFIX + "_hash:user2");
  }

  @Test
  @DisplayName("Test batch operations with custom batch size")
  void testBatchOperations() {
    // Create a large dataset
    List<Map<String, Object>> objects = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      objects.add(
          Map.of(
              "name", "User" + i,
              "age", 20 + (i % 50),
              "embedding", new float[] {i * 0.01f, i * 0.02f, i * 0.03f}));
    }

    // Write with small batch size
    List<String> keys = hashStorage.write(jedis, objects, null, null, null, 10);
    assertThat(keys).hasSize(100);

    // Get with small batch size
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys, 10);
    assertThat(retrieved).hasSize(100);
    assertThat(retrieved.get(0).get("name")).isEqualTo("User0");
    assertThat(retrieved.get(99).get("name")).isEqualTo("User99");
  }

  @Test
  @DisplayName("Test write with TTL")
  void testWriteWithTTL() throws InterruptedException {
    List<Map<String, Object>> objects = Arrays.asList(Map.of("name", "Temporary", "age", 30));

    // Write with 2 second TTL
    List<String> keys = hashStorage.write(jedis, objects, null, null, 2);
    assertThat(keys).hasSize(1);

    // Verify data exists
    assertThat(jedis.exists(keys.get(0))).isTrue();

    // Wait for TTL to expire
    TimeUnit.SECONDS.sleep(3);

    // Verify data has expired
    assertThat(jedis.exists(keys.get(0))).isFalse();
  }

  @Test
  @DisplayName("Test preprocessing function")
  void testPreprocessing() {
    // Define preprocessing function that converts names to uppercase
    Function<Map<String, Object>, Map<String, Object>> preprocessor =
        obj -> {
          Map<String, Object> processed = new HashMap<>(obj);
          if (processed.containsKey("name")) {
            processed.put("name", processed.get("name").toString().toUpperCase());
          }
          return processed;
        };

    List<Map<String, Object>> objects =
        Arrays.asList(Map.of("name", "john", "age", 30), Map.of("name", "jane", "age", 25));

    List<String> keys = hashStorage.write(jedis, objects, null, null, null, null, preprocessor);

    // Verify preprocessing was applied
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys);
    assertThat(retrieved.get(0).get("name")).isEqualTo("JOHN");
    assertThat(retrieved.get(1).get("name")).isEqualTo("JANE");
  }

  @Test
  @DisplayName("Test validation")
  void testValidation() {
    // Invalid data - age should be numeric
    List<Map<String, Object>> invalidObjects =
        Arrays.asList(Map.of("name", "John", "age", "thirty"));

    // Should throw validation error
    assertThatThrownBy(
            () -> hashStorage.write(jedis, invalidObjects, null, null, null, null, null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("validation");
  }

  @Test
  @DisplayName("Test ULID key generation")
  void testUlidKeyGeneration() {
    List<Map<String, Object>> objects =
        Arrays.asList(Map.of("name", "John", "age", 30), Map.of("name", "Jane", "age", 25));

    // Write without specifying keys or id field - should generate ULIDs
    List<String> keys = hashStorage.write(jedis, objects);
    assertThat(keys).hasSize(2);

    // ULID keys should be unique and start with correct prefix
    assertThat(keys.get(0)).startsWith(TEST_PREFIX + "_hash:");
    assertThat(keys.get(1)).startsWith(TEST_PREFIX + "_hash:");
    assertThat(keys.get(0)).isNotEqualTo(keys.get(1));

    // Keys should follow ULID format (26 characters after prefix)
    String ulid1 = keys.get(0).substring((TEST_PREFIX + "_hash:").length());
    String ulid2 = keys.get(1).substring((TEST_PREFIX + "_hash:").length());
    assertThat(ulid1).hasSize(26);
    assertThat(ulid2).hasSize(26);
  }

  @Test
  @DisplayName("Test empty operations")
  void testEmptyOperations() {
    // Test write with empty list
    List<String> keys = hashStorage.write(jedis, Collections.emptyList());
    assertThat(keys).isEmpty();

    // Test get with empty list
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, Collections.emptyList());
    assertThat(retrieved).isEmpty();
  }

  @Test
  @DisplayName("Test mismatched keys and objects")
  void testMismatchedKeysAndObjects() {
    List<Map<String, Object>> objects =
        Arrays.asList(Map.of("name", "John", "age", 30), Map.of("name", "Jane", "age", 25));

    List<String> wrongSizeKeys = Arrays.asList(TEST_PREFIX + "_hash:key1");

    // Should throw error for mismatched sizes
    assertThatThrownBy(() -> hashStorage.write(jedis, objects, null, wrongSizeKeys))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Length");
  }

  @Test
  @DisplayName("Test vector storage in Hash")
  void testVectorStorageInHash() {
    Map<String, Object> obj = Map.of("name", "Test", "embedding", new float[] {0.1f, 0.2f, 0.3f});

    List<String> keys = hashStorage.write(jedis, Arrays.asList(obj));
    assertThat(keys).hasSize(1);

    // Verify vector is stored as binary
    byte[] vectorBytes = jedis.hget(keys.get(0).getBytes(), "embedding".getBytes());
    assertThat(vectorBytes).isNotNull();
    assertThat(vectorBytes).hasSize(12); // 3 floats * 4 bytes

    // Verify retrieval
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys);
    assertThat(retrieved.get(0).get("embedding")).isInstanceOf(byte[].class);
  }

  @Test
  @DisplayName("Test vector storage in JSON")
  void testVectorStorageInJson() {
    Map<String, Object> obj = Map.of("name", "Test", "embedding", new float[] {0.1f, 0.2f, 0.3f});

    List<String> keys = jsonStorage.write(jedis, Arrays.asList(obj));
    assertThat(keys).hasSize(1);

    // Verify retrieval - JSON should return as List<Float>
    List<Map<String, Object>> retrieved = jsonStorage.get(jedis, keys);
    Object embedding = retrieved.get(0).get("embedding");
    assertThat(embedding).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Number> embeddingList = (List<Number>) embedding;
    assertThat(embeddingList).hasSize(3);
  }

  @Test
  @DisplayName("Test key formatting with various prefix and separator combinations")
  void testKeyFormatting() {
    // Test key without prefix and separator
    String key = "1234";
    String generated = BaseStorage.createKey(key, "", "");
    assertThat(generated).isEqualTo(key);

    // Test key without prefix but with separator
    generated = BaseStorage.createKey(key, "", ":");
    assertThat(generated).isEqualTo(key);

    // Test key with prefix and separator
    generated = BaseStorage.createKey(key, "test", ":");
    assertThat(generated).isEqualTo("test:" + key);

    // Test key with prefix but unusual separator
    generated = BaseStorage.createKey(key, "prefix", "_");
    assertThat(generated).isEqualTo("prefix_" + key);

    // Test key with complex prefix
    generated = BaseStorage.createKey(key, "app:env:service", ":");
    assertThat(generated).isEqualTo("app:env:service:" + key);
  }

  @Test
  @DisplayName("Test create key from object with id field")
  void testCreateKeyFromObject() {
    Map<String, Object> obj = Map.of("id", "1234", "name", "test");

    // Test with HashStorage
    String expectedKey = TEST_PREFIX + "_hash:1234";
    List<String> keys =
        hashStorage.write(jedis, Arrays.asList(obj), "id", null, null, null, null, false);
    assertThat(keys).hasSize(1);
    assertThat(keys.get(0)).isEqualTo(expectedKey);

    // Test with JsonStorage
    expectedKey = TEST_PREFIX + "_json:1234";
    keys = jsonStorage.write(jedis, Arrays.asList(obj), "id", null, null, null, null, false);
    assertThat(keys).hasSize(1);
    assertThat(keys.get(0)).isEqualTo(expectedKey);
  }

  @Test
  @DisplayName("Test validation with multiple field types")
  void testValidationMultipleFieldTypes() {
    // Test successful validation with all field types
    Map<String, Object> validData =
        Map.of(
            "name",
            "John Doe",
            "age",
            30,
            "tags",
            "engineer,developer",
            "embedding",
            new float[] {0.1f, 0.2f, 0.3f});

    // Should not throw for valid data
    List<String> keys =
        hashStorage.write(jedis, Arrays.asList(validData), null, null, null, null, null, true);
    assertThat(keys).hasSize(1);

    // Clean up
    jedis.del(keys.get(0));

    // Test validation failure for wrong numeric type
    Map<String, Object> invalidNumeric =
        Map.of(
            "name", "John",
            "age", "not-a-number");

    assertThatThrownBy(
            () ->
                hashStorage.write(
                    jedis, Arrays.asList(invalidNumeric), null, null, null, null, null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numeric");

    // Test validation failure for wrong vector type
    Map<String, Object> invalidVector = Map.of("embedding", "not-a-vector");

    assertThatThrownBy(
            () ->
                hashStorage.write(
                    jedis, Arrays.asList(invalidVector), null, null, null, null, null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vector");
  }

  @Test
  @DisplayName("Test preprocessing with null return handling")
  void testPreprocessingNullReturn() {
    List<Map<String, Object>> objects =
        Arrays.asList(
            Map.of("id", "1", "value", "keep"),
            Map.of("id", "2", "value", "skip"),
            Map.of("id", "3", "value", "keep"));

    Function<Map<String, Object>, Map<String, Object>> skipMiddle =
        obj -> {
          if ("skip".equals(obj.get("value"))) {
            return null; // Skip this object
          }
          return obj;
        };

    // Test that null preprocessing throws exception
    assertThatThrownBy(
            () -> hashStorage.write(jedis, objects, "id", null, null, null, skipMiddle, false))
        .isInstanceOf(com.redis.vl.exceptions.RedisVLException.class)
        .hasMessageContaining("Preprocess function returned null");
  }

  @Test
  @DisplayName("Test concurrent writes with same keys")
  void testConcurrentWrites() {
    // First write
    Map<String, Object> obj1 = Map.of("name", "First", "age", 25);
    List<String> keys1 =
        hashStorage.write(
            jedis,
            Arrays.asList(obj1),
            null,
            Arrays.asList(TEST_PREFIX + "_hash:concurrent"),
            null,
            null,
            null,
            false);

    // Second write with same key (should overwrite)
    Map<String, Object> obj2 = Map.of("name", "Second", "age", 30);
    List<String> keys2 =
        hashStorage.write(
            jedis,
            Arrays.asList(obj2),
            null,
            Arrays.asList(TEST_PREFIX + "_hash:concurrent"),
            null,
            null,
            null,
            false);

    assertThat(keys1.get(0)).isEqualTo(keys2.get(0));

    // Verify the second write overwrote the first
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys2);
    assertThat(retrieved).hasSize(1);
    assertThat(retrieved.get(0).get("name")).isEqualTo("Second");
    assertThat(retrieved.get(0).get("age").toString()).isEqualTo("30");
  }

  @Test
  @DisplayName("Test get with non-existent keys")
  void testGetNonExistentKeys() {
    List<String> nonExistentKeys =
        Arrays.asList(
            TEST_PREFIX + "_hash:does_not_exist_1", TEST_PREFIX + "_hash:does_not_exist_2");

    List<Map<String, Object>> results = hashStorage.get(jedis, nonExistentKeys);
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("Test mixed storage operations")
  void testMixedStorageOperations() {
    // Write to both storage types
    Map<String, Object> data =
        Map.of(
            "name",
            "Mixed Test",
            "age",
            40,
            "tags",
            "test",
            "embedding",
            new float[] {0.5f, 0.5f, 0.5f});

    List<String> hashKeys =
        hashStorage.write(jedis, Arrays.asList(data), null, null, null, null, null, false);
    List<String> jsonKeys =
        jsonStorage.write(jedis, Arrays.asList(data), null, null, null, null, null, false);

    assertThat(hashKeys).hasSize(1);
    assertThat(jsonKeys).hasSize(1);
    assertThat(hashKeys.get(0)).startsWith(TEST_PREFIX + "_hash:");
    assertThat(jsonKeys.get(0)).startsWith(TEST_PREFIX + "_json:");

    // Verify data is stored correctly in both formats
    List<Map<String, Object>> hashResults = hashStorage.get(jedis, hashKeys);
    List<Map<String, Object>> jsonResults = jsonStorage.get(jedis, jsonKeys);

    assertThat(hashResults).hasSize(1);
    assertThat(jsonResults).hasSize(1);
    assertThat(hashResults.get(0).get("name")).isEqualTo("Mixed Test");
    assertThat(jsonResults.get(0).get("name")).isEqualTo("Mixed Test");
  }

  @Test
  @DisplayName("Test batch size parameter effect")
  void testBatchSizeParameter() {
    // Create multiple objects
    List<Map<String, Object>> objects = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      objects.add(Map.of("id", String.valueOf(i), "name", "Test" + i));
    }

    // Write with different batch sizes (though currently not implemented, test the API)
    List<String> keys1 = hashStorage.write(jedis, objects, "id", null, null, 5, null, false);
    assertThat(keys1).hasSize(10);

    // Clean up and write again with different batch size
    keys1.forEach(jedis::del);
    List<String> keys2 = hashStorage.write(jedis, objects, "id", null, null, 2, null, false);
    assertThat(keys2).hasSize(10);

    // Verify all were written correctly
    List<Map<String, Object>> retrieved = hashStorage.get(jedis, keys2, 3);
    assertThat(retrieved).hasSize(10);
  }
}
