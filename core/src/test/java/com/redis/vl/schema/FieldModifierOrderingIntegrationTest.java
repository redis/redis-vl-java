package com.redis.vl.schema;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for field modifier ordering fix (PR #434).
 *
 * <p>Port of tests from
 * redis-vl-python/tests/integration/test_field_modifier_ordering_integration.py
 *
 * <p>These tests verify that field modifiers appear in the correct canonical order required by
 * RediSearch parser:
 *
 * <ul>
 *   <li>TextField: [INDEXEMPTY] [INDEXMISSING] [SORTABLE [UNF]] [NOINDEX]
 *   <li>TagField: [INDEXEMPTY] [INDEXMISSING] [SORTABLE] [NOINDEX]
 *   <li>NumericField: [INDEXMISSING] [SORTABLE [UNF]] [NOINDEX]
 *   <li>GeoField: [INDEXMISSING] [SORTABLE] [NOINDEX]
 * </ul>
 *
 * <p>Before this fix, using index_missing=true with sortable=true would fail because INDEXMISSING
 * appeared after SORTABLE, violating parser requirements.
 */
@Tag("integration")
public class FieldModifierOrderingIntegrationTest extends BaseIntegrationTest {

  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Tests will create their own indexes
  }

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
   * Test TextField with sortable=true and indexMissing=true.
   *
   * <p>Port of test_text_field_sortable_and_index_missing from Python.
   *
   * <p>This was the primary failure case before the fix - INDEXMISSING must appear before SORTABLE.
   */
  @Test
  void testTextFieldSortableAndIndexMissing() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_text_sortable_indexmissing")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTextField("title", field -> field.sortable(true).indexMissing(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);

    // This should not throw an exception
    assertDoesNotThrow(() -> index.create());

    // Verify we can query with ismissing filter
    // (This requires data, so we'll just verify index creation succeeded)
    assertTrue(index.exists());
  }

  /**
   * Test TextField with all modifiers: INDEXEMPTY, INDEXMISSING, SORTABLE, UNF.
   *
   * <p>Port of test_text_field_all_modifiers from Python.
   */
  @Test
  void testTextFieldAllModifiers() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_text_all_modifiers")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTextField(
                "description",
                field -> field.sortable(true).unf(true).indexMissing(true).indexEmpty(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test TagField with sortable=true and indexMissing=true.
   *
   * <p>Port of test_tag_field_sortable_and_index_missing from Python.
   */
  @Test
  void testTagFieldSortableAndIndexMissing() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_tag_sortable_indexmissing")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTagField("category", field -> field.sortable(true).indexMissing(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test TagField with INDEXEMPTY, INDEXMISSING, and SORTABLE.
   *
   * <p>Port of test_tag_field_all_modifiers from Python.
   */
  @Test
  void testTagFieldAllModifiers() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_tag_all_modifiers")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTagField("tags", field -> field.sortable(true).indexMissing(true).indexEmpty(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test NumericField with sortable=true and indexMissing=true.
   *
   * <p>Port of test_numeric_field_sortable_and_index_missing from Python.
   *
   * <p>Note: NumericField does not support INDEXEMPTY.
   */
  @Test
  void testNumericFieldSortableAndIndexMissing() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_numeric_sortable_indexmissing")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addNumericField("price", field -> field.sortable(true).indexMissing(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test NumericField with INDEXMISSING, SORTABLE, and UNF.
   *
   * <p>Port of test_numeric_field_with_modifiers from Python.
   */
  @Test
  void testNumericFieldWithModifiers() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_numeric_modifiers")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addNumericField("score", field -> field.sortable(true).unf(true).indexMissing(true))
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test GeoField with sortable=true and indexMissing=true.
   *
   * <p>Port of test_geo_field_sortable_and_index_missing from Python.
   *
   * <p>Note: GeoField does not support INDEXEMPTY or UNF.
   */
  @Test
  void testGeoFieldSortableAndIndexMissing() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_geo_sortable_indexmissing")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .field(GeoField.builder().name("location").sortable(true).indexMissing(true).build())
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }

  /**
   * Test mixed field types with various modifiers in a single index.
   *
   * <p>Port of test_mixed_field_types_with_modifiers from Python.
   */
  @Test
  void testMixedFieldTypesWithModifiers() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_mixed_fields")
            .storageType(StorageType.HASH)
            .prefix("doc:")
            .addTextField("title", field -> field.sortable(true).indexMissing(true))
            .addTagField(
                "category", field -> field.sortable(true).indexMissing(true).indexEmpty(true))
            .addNumericField("price", field -> field.sortable(true).indexMissing(true))
            .field(GeoField.builder().name("location").sortable(true).indexMissing(true).build())
            .build();

    index = new SearchIndex(schema, unifiedJedis);
    assertDoesNotThrow(() -> index.create());
    assertTrue(index.exists());
  }
}
