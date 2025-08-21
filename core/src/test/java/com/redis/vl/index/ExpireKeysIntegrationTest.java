package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.json.Path2;

/** Integration tests for expire keys functionality */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Expire Keys Integration Tests")
class ExpireKeysIntegrationTest extends BaseIntegrationTest {

  private static SearchIndex index;
  private static final String TEST_PREFIX = "expire:";

  @BeforeAll
  static void setup() {
    // Create test index
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_expire");
    indexConfig.put("storage_type", "json");
    indexConfig.put("prefix", TEST_PREFIX);
    schemaDict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of(
                "name", "$.name",
                "type", "text"),
            Map.of(
                "name", "$.value",
                "type", "numeric"));
    schemaDict.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, unifiedJedis);
    index.create(true);
  }

  @AfterAll
  static void cleanup() {
    if (index != null) {
      try {
        // Clean up any remaining test documents
        for (int i = 0; i < 10; i++) {
          unifiedJedis.del(TEST_PREFIX + "doc" + i);
        }
        index.drop();
      } catch (Exception e) {
        // Ignore errors during cleanup
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test expire single key")
  void testExpireSingleKey() throws InterruptedException {
    // Create a test document
    String key = TEST_PREFIX + "doc_expire_single";
    Map<String, Object> doc = Map.of("name", "test document", "value", 42);
    unifiedJedis.jsonSetWithEscape(key, Path2.of("$"), doc);

    // Verify document exists
    assertThat(unifiedJedis.exists(key)).isTrue();

    // Set expiration for 2 seconds
    index.expireKeys(key, 2);

    // Check TTL is set
    long ttl = unifiedJedis.ttl(key);
    assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(2);

    // Wait for expiration
    Thread.sleep(2500);

    // Verify document is expired
    assertThat(unifiedJedis.exists(key)).isFalse();
  }

  @Test
  @Order(2)
  @DisplayName("Test expire multiple keys")
  void testExpireMultipleKeys() throws InterruptedException {
    // Create multiple test documents
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      String key = TEST_PREFIX + "doc_expire_" + i;
      keys.add(key);
      Map<String, Object> doc = Map.of("name", "document " + i, "value", i * 10);
      unifiedJedis.jsonSetWithEscape(key, Path2.of("$"), doc);
    }

    // Verify all documents exist
    for (String key : keys) {
      assertThat(unifiedJedis.exists(key)).isTrue();
    }

    // Set expiration for all keys to 2 seconds
    List<Long> results = index.expireKeys(keys, 2);

    // Verify all expirations were set successfully (returns 1 for success)
    assertThat(results).hasSize(keys.size());
    for (Long result : results) {
      assertThat(result).isEqualTo(1L);
    }

    // Check TTL is set for all keys
    for (String key : keys) {
      long ttl = unifiedJedis.ttl(key);
      assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(2);
    }

    // Wait for expiration
    Thread.sleep(2500);

    // Verify all documents are expired
    for (String key : keys) {
      assertThat(unifiedJedis.exists(key)).isFalse();
    }
  }

  @Test
  @Order(3)
  @DisplayName("Test expire keys with different TTLs")
  void testExpireKeysWithDifferentTTLs() throws InterruptedException {
    // Create documents with different expiration times
    String shortLivedKey = TEST_PREFIX + "short_lived";
    String longLivedKey = TEST_PREFIX + "long_lived";

    Map<String, Object> doc1 = Map.of("name", "short", "value", 1);
    Map<String, Object> doc2 = Map.of("name", "long", "value", 2);

    unifiedJedis.jsonSetWithEscape(shortLivedKey, Path2.of("$"), doc1);
    unifiedJedis.jsonSetWithEscape(longLivedKey, Path2.of("$"), doc2);

    // Set different expiration times
    index.expireKeys(shortLivedKey, 1); // 1 second
    index.expireKeys(longLivedKey, 5); // 5 seconds

    // Wait 2 seconds
    Thread.sleep(2000);

    // Short-lived should be expired, long-lived should still exist
    assertThat(unifiedJedis.exists(shortLivedKey)).isFalse();
    assertThat(unifiedJedis.exists(longLivedKey)).isTrue();

    // Clean up
    unifiedJedis.del(longLivedKey);
  }

  @Test
  @Order(4)
  @DisplayName("Test expire non-existent key")
  void testExpireNonExistentKey() {
    String nonExistentKey = TEST_PREFIX + "non_existent";

    // Ensure key doesn't exist
    assertThat(unifiedJedis.exists(nonExistentKey)).isFalse();

    // Try to expire non-existent key
    index.expireKeys(nonExistentKey, 10);

    // Should not throw exception, just no effect
    assertThat(unifiedJedis.exists(nonExistentKey)).isFalse();
  }

  @Test
  @Order(5)
  @DisplayName("Test expire keys affects search results")
  void testExpireKeysAffectsSearchResults() throws InterruptedException {
    // Create test documents
    String expiringSoonKey = TEST_PREFIX + "expiring_soon";
    String persistentKey = TEST_PREFIX + "persistent";

    Map<String, Object> doc1 = Map.of("name", "temporary data", "value", 100);
    Map<String, Object> doc2 = Map.of("name", "permanent data", "value", 200);

    unifiedJedis.jsonSetWithEscape(expiringSoonKey, Path2.of("$"), doc1);
    unifiedJedis.jsonSetWithEscape(persistentKey, Path2.of("$"), doc2);

    // Initial search should return both documents
    List<Map<String, Object>> initialResults = index.query("*");
    int initialCount = initialResults.size();
    assertThat(initialCount).isGreaterThanOrEqualTo(2);

    // Expire one document
    index.expireKeys(expiringSoonKey, 1);

    // Wait for expiration
    Thread.sleep(1500);

    // Search again - should have one less document
    List<Map<String, Object>> afterExpireResults = index.query("*");
    assertThat(afterExpireResults.size()).isLessThan(initialCount);

    // Clean up
    unifiedJedis.del(persistentKey);
  }

  @Test
  @Order(6)
  @DisplayName("Test batch expire with mixed results")
  void testBatchExpireWithMixedResults() {
    // Create some keys that exist and some that don't
    List<String> keys =
        Arrays.asList(
            TEST_PREFIX + "exists1",
            TEST_PREFIX + "not_exists1",
            TEST_PREFIX + "exists2",
            TEST_PREFIX + "not_exists2");

    // Create only the "exists" keys
    Map<String, Object> doc = Map.of("name", "test", "value", 1);
    unifiedJedis.jsonSetWithEscape(keys.get(0), Path2.of("$"), doc);
    unifiedJedis.jsonSetWithEscape(keys.get(2), Path2.of("$"), doc);

    // Expire all keys (including non-existent ones)
    List<Long> results = index.expireKeys(keys, 10);

    // Check results - 1 for success, 0 for key doesn't exist
    assertThat(results).hasSize(4);
    assertThat(results.get(0)).isEqualTo(1L); // exists1 - success
    assertThat(results.get(1)).isEqualTo(0L); // not_exists1 - doesn't exist
    assertThat(results.get(2)).isEqualTo(1L); // exists2 - success
    assertThat(results.get(3)).isEqualTo(0L); // not_exists2 - doesn't exist

    // Clean up
    unifiedJedis.del(keys.get(0), keys.get(2));
  }
}
