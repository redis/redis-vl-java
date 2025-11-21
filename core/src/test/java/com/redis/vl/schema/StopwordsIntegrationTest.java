package com.redis.vl.schema;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema.StorageType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for index-level stopwords support (PR #436).
 *
 * <p>Port of tests from redis-vl-python/tests/integration/test_stopwords_integration.py
 *
 * <p>These tests verify:
 *
 * <ul>
 *   <li>Creating indices with default stopwords (null)
 *   <li>Creating indices with disabled stopwords (empty list)
 *   <li>Creating indices with custom stopwords list
 *   <li>Ability to search for common words when stopwords are disabled
 * </ul>
 */
@Tag("integration")
public class StopwordsIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;

  @AfterEach
  void tearDown() {
    if (index != null) {
      try {
        index.delete(true);
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
  }

  /**
   * Test creating an index with default stopwords (null).
   *
   * <p>Port of test_create_index_with_default_stopwords from Python.
   */
  @Test
  void testCreateIndexWithDefaultStopwords() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_default_stopwords")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTextField("content", field -> {})
            .build();

    // Verify stopwords is null (default)
    assertNull(schema.getIndex().getStopwords());

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test creating an index with stopwords disabled (empty list).
   *
   * <p>Port of test_create_index_with_disabled_stopwords from Python.
   *
   * <p>When stopwords are disabled (STOPWORDS 0), all words including common terms like "of",
   * "the", "a" are indexed and searchable.
   */
  @Test
  void testCreateIndexWithDisabledStopwords() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_disabled_stopwords")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .stopwords(Collections.emptyList())
            .addTextField("content", field -> {})
            .build();

    // Verify stopwords is empty list
    assertNotNull(schema.getIndex().getStopwords());
    assertTrue(schema.getIndex().getStopwords().isEmpty());

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test creating an index with disabled stopwords using noStopwords() builder method.
   *
   * <p>Port of test_create_index_with_no_stopwords_builder from Python.
   */
  @Test
  void testCreateIndexWithNoStopwordsBuilder() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_no_stopwords_builder")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .noStopwords()
            .addTextField("content", field -> {})
            .build();

    // Verify stopwords is empty list
    assertNotNull(schema.getIndex().getStopwords());
    assertTrue(schema.getIndex().getStopwords().isEmpty());

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test creating an index with custom stopwords list.
   *
   * <p>Port of test_create_index_with_custom_stopwords from Python.
   */
  @Test
  void testCreateIndexWithCustomStopwords() {
    List<String> customStopwords = Arrays.asList("foo", "bar", "baz");

    IndexSchema schema =
        IndexSchema.builder()
            .name("test_custom_stopwords")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .stopwords(customStopwords)
            .addTextField("content", field -> {})
            .build();

    // Verify stopwords matches custom list
    assertNotNull(schema.getIndex().getStopwords());
    assertEquals(3, schema.getIndex().getStopwords().size());
    assertTrue(schema.getIndex().getStopwords().contains("foo"));
    assertTrue(schema.getIndex().getStopwords().contains("bar"));
    assertTrue(schema.getIndex().getStopwords().contains("baz"));

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test that common words can be searched when stopwords are disabled.
   *
   * <p>Port of test_search_with_disabled_stopwords from Python.
   *
   * <p>This test verifies that when stopwords are disabled, common words like "of" that would
   * normally be filtered out are now indexed and searchable.
   */
  @Test
  void testSearchWithDisabledStopwords() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_search_disabled_stopwords")
            .storageType(StorageType.HASH)
            .prefix("search:")
            .noStopwords()
            .addTextField("name", field -> {})
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    index.create();

    // Add test data containing common stopwords
    unifiedJedis.hset("search:1", "name", "Bank of America");
    unifiedJedis.hset("search:2", "name", "Bank of England");
    unifiedJedis.hset("search:3", "name", "Bank the River");

    // Search for "of" - should find docs containing "of" since stopwords are disabled
    var results = unifiedJedis.ftSearch(index.getName(), "of");
    assertTrue(
        results.getTotalResults() >= 2,
        "Should find documents containing 'of' when stopwords are disabled");
  }

  /**
   * Test creating multiple indices with different stopwords configurations.
   *
   * <p>Port of test_multiple_indices_different_stopwords from Python.
   */
  @Test
  void testMultipleIndicesDifferentStopwords() {
    // Index with default stopwords
    IndexSchema schema1 =
        IndexSchema.builder()
            .name("test_multi_default")
            .storageType(StorageType.HASH)
            .prefix("default:")
            .addTextField("content", field -> {})
            .build();

    SearchIndex index1 = new SearchIndex(schema1, unifiedJedis);
    assertDoesNotThrow(() -> index1.create());
    assertTrue(index1.exists());

    // Index with disabled stopwords
    IndexSchema schema2 =
        IndexSchema.builder()
            .name("test_multi_disabled")
            .storageType(StorageType.HASH)
            .prefix("disabled:")
            .noStopwords()
            .addTextField("content", field -> {})
            .build();

    SearchIndex index2 = new SearchIndex(schema2, unifiedJedis);
    assertDoesNotThrow(() -> index2.create());
    assertTrue(index2.exists());

    // Cleanup
    index1.delete(true);
    index2.delete(true);
  }
}
