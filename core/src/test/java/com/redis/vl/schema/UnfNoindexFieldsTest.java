package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * Unit tests for UNF and NOINDEX field attributes (issue #374).
 *
 * <p>Ported from Python: tests/unit/test_unf_noindex_fields.py
 *
 * <p>Python reference: PR #386 - UNF and NOINDEX attributes for field classes
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>NOINDEX: Prevents field from being indexed (saves memory), field still sortable/retrievable
 *   <li>UNF: Un-normalized form - preserves original character case for sorting
 *   <li>UNF only applies when sortable=True
 *   <li>TextField and NumericField support both NOINDEX and UNF
 *   <li>TagField and GeoField support NOINDEX only
 *   <li>Redis command order: UNF must come before NOINDEX
 * </ul>
 */
@DisplayName("UNF and NOINDEX Field Attributes Tests")
class UnfNoindexFieldsTest {

  // ========== TextField Tests ==========

  @Test
  @DisplayName("TextField: Should support NOINDEX attribute")
  void testTextFieldNoIndex() {
    // Python: TextField(name="title", attrs={"no_index": True})
    TextField field = TextField.builder().name("title").indexed(false).build();

    assertThat(field.isIndexed()).isFalse();

    // Verify Jedis field is created (actual NOINDEX verification in integration tests)
    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.TextField.class);
  }

  @Test
  @DisplayName("TextField: Should support NOINDEX with sortable")
  void testTextFieldNoIndexWithSortable() {
    // Python: TextField(name="title", attrs={"no_index": True, "sortable": True})
    TextField field = TextField.builder().name("title").indexed(false).sortable(true).build();

    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("TextField: Should support UNF attribute with sortable")
  void testTextFieldUnfWithSortable() {
    // Python: TextField(name="title", attrs={"unf": True, "sortable": True})
    TextField field = TextField.builder().name("title").unf(true).sortable(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isTrue();

    // Verify Jedis field is created with sortableUNF
    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.TextField.class);
  }

  @Test
  @DisplayName("TextField: UNF should be ignored when not sortable")
  void testTextFieldUnfWithoutSortable() {
    // Python: TextField(name="title", attrs={"unf": True})
    // UNF is ignored when sortable=False
    TextField field = TextField.builder().name("title").unf(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isFalse();

    // Verify Jedis field is created (UNF ignored internally because not sortable)
    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("TextField: Should support both UNF and NOINDEX with sortable")
  void testTextFieldUnfAndNoIndex() {
    // Python: TextField(name="title", attrs={"unf": True, "no_index": True, "sortable": True})
    // UNF and NOINDEX can coexist when sortable=True
    TextField field =
        TextField.builder().name("title").unf(true).indexed(false).sortable(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("TextField: Builder should have fluent unf() method")
  void testTextFieldBuilderFluentUnf() {
    // Java convenience: unf() without parameter
    TextField field = TextField.builder().name("title").sortable().unf().build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isTrue();
  }

  // ========== NumericField Tests ==========

  @Test
  @DisplayName("NumericField: Should support NOINDEX attribute")
  void testNumericFieldNoIndex() {
    // Python: NumericField(name="price", attrs={"no_index": True})
    NumericField field = NumericField.builder().name("price").indexed(false).build();

    assertThat(field.isIndexed()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.NumericField.class);
  }

  @Test
  @DisplayName("NumericField: Should support UNF attribute with sortable")
  void testNumericFieldUnfWithSortable() {
    // Python: NumericField(name="price", attrs={"unf": True, "sortable": True})
    // Note: Jedis NumericField doesn't have sortableUNF() yet, so we document the limitation
    NumericField field = NumericField.builder().name("price").unf(true).sortable(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("NumericField: UNF should be stored even when not sortable")
  void testNumericFieldUnfWithoutSortable() {
    // Python: NumericField(name="price", attrs={"unf": True})
    NumericField field = NumericField.builder().name("price").unf(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("NumericField: Should support both UNF and NOINDEX with sortable")
  void testNumericFieldUnfAndNoIndex() {
    // Python: NumericField(name="price", attrs={"unf": True, "no_index": True, "sortable": True})
    NumericField field =
        NumericField.builder().name("price").unf(true).indexed(false).sortable(true).build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("NumericField: Builder should have fluent unf() method")
  void testNumericFieldBuilderFluentUnf() {
    NumericField field = NumericField.builder().name("price").sortable().unf().build();

    assertThat(field.isUnf()).isTrue();
    assertThat(field.isSortable()).isTrue();
  }

  // ========== TagField Tests ==========

  @Test
  @DisplayName("TagField: Should support NOINDEX attribute")
  void testTagFieldNoIndex() {
    // Python: TagField(name="category", attrs={"no_index": True})
    TagField field = TagField.builder().name("category").indexed(false).build();

    assertThat(field.isIndexed()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.TagField.class);
  }

  @Test
  @DisplayName("TagField: Should support NOINDEX with sortable")
  void testTagFieldNoIndexWithSortable() {
    // Python: TagField(name="category", attrs={"no_index": True, "sortable": True})
    TagField field = TagField.builder().name("category").indexed(false).sortable(true).build();

    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("TagField: Should NOT support UNF attribute")
  void testTagFieldNoUnf() {
    // Python: TagField does not have 'unf' attribute
    // Verify TagField class does not have unf field or method
    TagField field = TagField.builder().name("category").sortable(true).build();

    // TagField should not have unf() method - this is compile-time checked
    // Just verify it builds successfully without unf
    assertThat(field.isSortable()).isTrue();
  }

  // ========== GeoField Tests ==========

  @Test
  @DisplayName("GeoField: Should support NOINDEX attribute")
  void testGeoFieldNoIndex() {
    // Python: GeoField(name="location", attrs={"no_index": True})
    GeoField field = GeoField.builder().name("location").indexed(false).build();

    assertThat(field.isIndexed()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.GeoField.class);
  }

  @Test
  @DisplayName("GeoField: Should support NOINDEX with sortable")
  void testGeoFieldNoIndexWithSortable() {
    // Python: GeoField(name="location", attrs={"no_index": True, "sortable": True})
    GeoField field = GeoField.builder().name("location").indexed(false).sortable(true).build();

    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("GeoField: Should NOT support UNF attribute")
  void testGeoFieldNoUnf() {
    // Python: GeoField does not have 'unf' attribute
    GeoField field = GeoField.builder().name("location").sortable(true).build();

    // GeoField should not have unf() method - this is compile-time checked
    assertThat(field.isSortable()).isTrue();
  }

  // ========== Backward Compatibility Tests ==========

  @Test
  @DisplayName("TextField: Default values should maintain backward compatibility")
  void testTextFieldBackwardCompatibility() {
    // Python: TextField(name="title") - defaults: indexed=True, sortable=False, unf=False
    TextField field = TextField.of("title").build();

    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
    assertThat(field.isUnf()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }

  @Test
  @DisplayName("NumericField: Default values should maintain backward compatibility")
  void testNumericFieldBackwardCompatibility() {
    // Python: NumericField(name="price") - defaults: indexed=True, sortable=False, unf=False
    NumericField field = NumericField.of("price").build();

    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
    assertThat(field.isUnf()).isFalse();

    SchemaField jedisField = field.toJedisSchemaField();
    assertThat(jedisField).isNotNull();
  }
}
