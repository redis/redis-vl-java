package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for IndexSchema prefix handling - ported from Python test_convert_index_info.py
 *
 * <p>Python reference: /redis-vl-python/tests/unit/test_convert_index_info.py
 *
 * <p>Tests handling of single and multiple prefixes in index schema, including backward
 * compatibility normalization (issue #258/#392).
 */
@DisplayName("IndexSchema Prefix Handling Tests")
class IndexSchemaPrefixTest {

  /**
   * Port of Python test_convert_index_info_single_prefix
   *
   * <p>Single-element prefix lists should be normalized to strings for backward compatibility.
   */
  @Test
  @DisplayName("Should normalize single-element prefix list to string")
  void testSinglePrefixNormalization() {
    // Create schema with single prefix as list
    IndexSchema schema =
        IndexSchema.builder().name("test_index").prefix(List.of("prefix_a")).build();

    // Should be normalized to string for backward compatibility
    Object prefix = schema.getIndex().getPrefixRaw();
    assertThat(prefix).isInstanceOf(String.class);
    assertThat(prefix).isEqualTo("prefix_a");

    // The normalized prefix method should also return the string
    assertThat(schema.getIndex().getPrefix()).isEqualTo("prefix_a");
  }

  /**
   * Port of Python test_convert_index_info_multiple_prefixes
   *
   * <p>Multiple prefixes should be preserved as a list (issue #258/#392).
   */
  @Test
  @DisplayName("Should preserve multiple prefixes as list")
  void testMultiplePrefixPreservation() {
    // Create schema with multiple prefixes
    List<String> prefixes = List.of("prefix_a", "prefix_b", "prefix_c");
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix(prefixes).build();

    // Multiple prefixes should be preserved as list
    Object prefixRaw = schema.getIndex().getPrefixRaw();
    assertThat(prefixRaw).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<String> prefixList = (List<String>) prefixRaw;
    assertThat(prefixList).containsExactly("prefix_a", "prefix_b", "prefix_c");

    // The normalized prefix method should return the first prefix
    assertThat(schema.getIndex().getPrefix()).isEqualTo("prefix_a");
  }

  /** Test that the normalized prefix method always returns the first prefix for key construction */
  @Test
  @DisplayName("Should return first prefix from getPrefix() for multiple prefixes")
  void testGetPrefixReturnsFirst() {
    List<String> prefixes = List.of("prefix_a", "prefix_b", "prefix_c");
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix(prefixes).build();

    // getPrefix() should return first prefix for Redis key construction
    assertThat(schema.getIndex().getPrefix()).isEqualTo("prefix_a");
  }

  /** Test that single string prefix works (backward compatibility) */
  @Test
  @DisplayName("Should support single string prefix (backward compatibility)")
  void testSingleStringPrefix() {
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("my_prefix").build();

    Object prefixRaw = schema.getIndex().getPrefixRaw();
    assertThat(prefixRaw).isInstanceOf(String.class);
    assertThat(prefixRaw).isEqualTo("my_prefix");
    assertThat(schema.getIndex().getPrefix()).isEqualTo("my_prefix");
  }

  /** Test that prefix can be null (use default) */
  @Test
  @DisplayName("Should handle null prefix")
  void testNullPrefix() {
    IndexSchema schema = IndexSchema.builder().name("test_index").build();

    assertThat(schema.getIndex().getPrefix()).isNull();
    assertThat(schema.getIndex().getPrefixRaw()).isNull();
  }

  /** Test serialization to Map preserves multiple prefixes */
  @Test
  @DisplayName("Should preserve multiple prefixes in serialization")
  void testMultiplePrefixSerialization() {
    List<String> prefixes = List.of("prefix_a", "prefix_b", "prefix_c");
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix(prefixes).build();

    // Convert to YAML and back
    String yaml = schema.toYaml();
    assertThat(yaml).contains("prefix_a");
    assertThat(yaml).contains("prefix_b");
    assertThat(yaml).contains("prefix_c");
  }
}
