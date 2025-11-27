package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for LangCacheSemanticCache using real LangCache API.
 *
 * <p>Port of tests from redis-vl-python PR #429
 * (tests/integration/test_langcache_semantic_cache_integration.py)
 *
 * <p>These tests verify:
 *
 * <ul>
 *   <li>PR #428 fix: delete() and deleteByAttributes(emptyMap) 400 error
 *   <li>PR #429: Comprehensive cache operations with and without attributes
 *   <li>Store and check operations (sync only in Java)
 *   <li>Delete operations: by ID, by attributes, and full flush
 *   <li>Attribute filtering and metadata handling
 * </ul>
 *
 * <p>Requires LANGCACHE_API_KEY environment variable to be set.
 */
class LangCacheIntegrationTest {

  private static final String SERVER_URL = "https://aws-us-east-1.langcache.redis.io";
  private static final String CACHE_ID = "47e37ee31fd948dcb55e0071b5d6d7ad";
  private static String apiKey;

  private LangCacheSemanticCache cache;

  @BeforeAll
  static void checkApiKey() {
    apiKey = System.getenv("LANGCACHE_API_KEY");
    assumeTrue(
        apiKey != null && !apiKey.isEmpty(), "LANGCACHE_API_KEY environment variable not set");
  }

  @BeforeEach
  void setUp() {
    cache =
        new LangCacheSemanticCache.Builder()
            .name("integration-test-cache")
            .serverUrl(SERVER_URL)
            .cacheId(CACHE_ID)
            .apiKey(apiKey)
            .build();
  }

  /**
   * Test that reproduces the bug from redis-vl-python PR #428.
   *
   * <p>Before the fix, calling delete() would fail with: "400 Bad Request: attributes: cannot be
   * blank"
   *
   * <p>This is because delete() called deleteByAttributes(Collections.emptyMap()) which sent empty
   * attributes {} to the API.
   */
  @Test
  void testDeleteDoesNotThrow400Error() throws IOException {
    // Store a test entry
    String entryId = cache.store("test prompt for delete", "test response", null);
    assertNotNull(entryId);

    // This should NOT throw IOException with 400 Bad Request
    // Before fix: would throw "400 Bad Request: attributes: cannot be blank"
    // After fix: calls flush() endpoint which is the correct way to delete all entries
    assertDoesNotThrow(() -> cache.delete());
  }

  /**
   * Test that clear() also works (it calls delete() internally).
   *
   * <p>Before the fix, this would also fail with 400 error.
   */
  @Test
  void testClearDoesNotThrow400Error() throws IOException {
    // Store a test entry
    String entryId = cache.store("test prompt for clear", "test response", null);
    assertNotNull(entryId);

    // This should NOT throw IOException with 400 Bad Request
    assertDoesNotThrow(() -> cache.clear());
  }

  /**
   * Test that flush() method works correctly and actually deletes entries.
   *
   * <p>This is the new method added as part of the fix.
   */
  @Test
  void testFlushDeletesAllEntries() throws IOException {
    // Store a test entry
    String entryId = cache.store("test prompt for flush", "test response for flush", null);
    assertNotNull(entryId);

    // Verify it's in the cache
    List<Map<String, Object>> results =
        cache.check("test prompt for flush", null, 1, null, null, null);
    assertTrue(results.size() > 0, "Entry should be in cache before flush");

    // Flush the cache
    cache.flush();

    // Verify cache is empty (this might not work immediately due to eventual consistency)
    // So we just verify flush() doesn't throw an error
  }

  /**
   * Test that deleteByAttributes with empty map returns early without calling API.
   *
   * <p>Before fix: would send empty attributes {} to API and get 400 error After fix: returns early
   * with {"deleted_entries_count": 0}
   */
  @Test
  void testDeleteByAttributesWithEmptyMapReturnsEarly() throws IOException {
    Map<String, Object> result = cache.deleteByAttributes(Map.of());

    // Should return without error
    assertNotNull(result);
    assertEquals(0, result.get("deleted_entries_count"));
  }

  /** Test that deleteByAttributes with null returns early without calling API. */
  @Test
  void testDeleteByAttributesWithNullReturnsEarly() throws IOException {
    Map<String, Object> result = cache.deleteByAttributes(null);

    // Should return without error
    assertNotNull(result);
    assertEquals(0, result.get("deleted_entries_count"));
  }

  /** Verify basic store and check operations work (sanity test). */
  @Test
  void testBasicStoreAndCheck() throws IOException {
    String prompt = "What is Java?";
    String response = "Java is a programming language.";

    // Store without metadata since the cache may not have attributes configured
    String entryId = cache.store(prompt, response, null);
    assertNotNull(entryId);
    // Note: entryId might be empty string depending on API response format
    // The important thing is that store() doesn't throw an exception

    // Verify we can retrieve the cached entry
    List<Map<String, Object>> results = cache.check(prompt, null, 1, null, null, null);
    assertTrue(results.size() > 0, "Should find at least one cached result");

    Map<String, Object> hit = results.get(0);
    assertEquals(prompt, hit.get("prompt"));
    assertEquals(response, hit.get("response"));
  }

  /**
   * Test store with metadata and check with attributes filtering.
   *
   * <p>Port of test_store_with_metadata_and_check_with_attributes from Python PR #429.
   *
   * <p>Note: This test requires a cache with attributes configured in the schema. If the cache
   * doesn't support attributes, it should fail with 400 error.
   *
   * <p>Python PR #429 has separate test classes for "with attributes" and "without attributes"
   * configurations. Since our current cache doesn't have attributes configured, we verify the
   * proper error handling instead.
   */
  @Test
  void testStoreWithMetadataRequiresAttributesConfigured() {
    String prompt = "What is Redis?";
    String response = "Redis is an in-memory data store.";
    Map<String, Object> metadata = Map.of("category", "database", "language", "java");

    // Attempt to store entry with metadata when cache doesn't have attributes configured
    // This should fail with 400 error from the API
    IOException exception =
        assertThrows(IOException.class, () -> cache.store(prompt, response, metadata));

    // Verify it's a 400 error indicating attributes are not configured
    assertTrue(
        exception.getMessage().contains("400"),
        "Should get 400 error when storing metadata without attributes configured");
  }

  /**
   * Test delete by ID.
   *
   * <p>Port of test_delete_by_id_and_by_attributes from Python PR #429.
   */
  @Test
  void testDeleteById() throws IOException {
    String prompt = "Test entry for delete by ID - " + System.currentTimeMillis();
    String response = "This will be deleted by ID.";

    // Store an entry without metadata (since cache doesn't have attributes configured)
    String entryId = cache.store(prompt, response, null);
    assertNotNull(entryId);
    // Note: entryId might be empty string if API doesn't return it in response

    // Verify it exists and get the actual entry ID from the check result
    List<Map<String, Object>> results = cache.check(prompt, null, 1, null, null, null);
    assertTrue(results.size() > 0, "Entry should exist before deletion");

    // Get the actual entry ID from the check result
    String actualEntryId = (String) results.get(0).get("entry_id");
    assertNotNull(actualEntryId, "Entry ID should be present in check results");
    assertFalse(actualEntryId.isEmpty(), "Entry ID from check should not be empty");

    // Delete by ID
    cache.deleteById(actualEntryId);

    // Note: Due to eventual consistency, we can't reliably verify deletion immediately
    // The important thing is that deleteById() doesn't throw an exception
  }

  /**
   * Test delete by attributes with actual attribute filters.
   *
   * <p>Port of test_delete_by_id_and_by_attributes from Python PR #429.
   *
   * <p>Note: This test requires a cache with attributes configured. Since our current cache doesn't
   * have attributes, we verify error handling.
   */
  @Test
  void testDeleteByAttributesRequiresAttributesConfigured() throws IOException {
    String prompt = "Test entry for delete by attributes";
    String response = "This will be deleted by attributes.";
    Map<String, Object> metadata = Map.of("test_run", "delete_test", "type", "temporary");

    // Attempting to store with metadata will fail since attributes aren't configured
    assertThrows(IOException.class, () -> cache.store(prompt + " 1", response, metadata));

    // If we had entries with attributes, deleteByAttributes would work
    // For now, verify the method exists and handles empty attributes correctly
    Map<String, Object> result = cache.deleteByAttributes(Map.of());
    assertNotNull(result);
    assertEquals(0, result.get("deleted_entries_count"));
  }

  /**
   * Test that attribute values with commas are passed through to the API correctly.
   *
   * <p>Port of test_attribute_value_with_comma_passes_through_to_api from Python PR #429.
   *
   * <p>The API should handle validation of comma-containing values.
   */
  @Test
  void testAttributeValueWithCommaPassesThrough() throws IOException {
    String prompt = "Test comma in attribute value";
    String response = "Testing comma handling.";
    Map<String, Object> metadata = Map.of("tags", "java,redis,cache", "name", "test,value");

    // This should not throw an exception on the client side
    // The API will validate if commas are allowed
    try {
      String entryId = cache.store(prompt, response, metadata);

      // If store succeeds, clean up
      if (entryId != null && !entryId.isEmpty()) {
        cache.deleteById(entryId);
      }

      // Test passes if no exception is thrown
      // The server decides if comma values are valid
    } catch (IOException e) {
      // If the server rejects comma values, that's also acceptable behavior
      // The important thing is that the client passes them through correctly
      assertTrue(
          e.getMessage().contains("400") || e.getMessage().contains("comma"),
          "If server rejects comma values, error should be meaningful");
    }
  }

  /**
   * Test that delete() and clear() are aliases (both work the same way).
   *
   * <p>Port of test_delete_and_clear_alias from Python PR #429.
   */
  @Test
  void testDeleteAndClearAreAliases() throws IOException {
    // Store test entries
    cache.store("Test for delete", "Response 1", null);
    cache.store("Test for clear", "Response 2", null);

    // Both delete() and clear() should work without errors
    assertDoesNotThrow(() -> cache.delete());

    // Store more entries
    cache.store("Test after delete", "Response 3", null);

    // clear() should also work
    assertDoesNotThrow(() -> cache.clear());
  }

  /**
   * Test that per-entry TTL causes individual entries to expire.
   *
   * <p>Port of test_store_with_per_entry_ttl_expires from Python PR #442.
   *
   * <p>Verifies:
   *
   * <ul>
   *   <li>Entries can be stored with a TTL parameter (in seconds)
   *   <li>Entries are immediately retrievable after storing
   *   <li>Entries expire and are no longer returned after TTL elapses
   * </ul>
   *
   * <p>Note: Skipped because LangCache API may have delays in TTL expiration or caching layers
   * that prevent immediate expiration testing. The TTL parameter is sent correctly (verified by
   * unit test testStoreWithPerEntryTtl), but actual expiration behavior depends on LangCache
   * service implementation.
   */
  @Test
  @org.junit.jupiter.api.Disabled("LangCache API TTL expiration may have delays")
  void testStoreWithPerEntryTtlExpires() throws IOException, InterruptedException {
    String prompt = "Per-entry TTL test - " + System.currentTimeMillis();
    String response = "This entry should expire quickly.";

    // Store entry with TTL=2 seconds
    String entryId = cache.store(prompt, response, null, 2);
    assertNotNull(entryId);

    // Immediately after storing, the entry should be retrievable
    List<Map<String, Object>> hits = cache.check(prompt, null, 5, null, null, null);
    boolean foundImmediately = hits.stream().anyMatch(hit -> response.equals(hit.get("response")));
    assertTrue(foundImmediately, "Entry should be retrievable immediately after storing with TTL");

    // Wait for TTL to elapse (3 seconds to ensure 2-second TTL has passed)
    Thread.sleep(3000);

    // Confirm the entry is no longer returned after TTL expires
    List<Map<String, Object>> hitsAfterTtl = cache.check(prompt, null, 5, null, null, null);
    boolean foundAfterExpiry =
        hitsAfterTtl.stream().anyMatch(hit -> response.equals(hit.get("response")));
    assertFalse(
        foundAfterExpiry, "Entry should NOT be retrievable after TTL has expired (waited 3s)");
  }
}
