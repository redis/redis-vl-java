package com.redis.vl.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for key separator handling in BaseStorage.
 *
 * <p>Tests the fix for issue #368: Handle key separators correctly to avoid double separators when
 * prefix ends with the separator character.
 *
 * <p>Python port: Ensures BaseStorage.createKey() normalizes prefixes by stripping trailing
 * separators, matching the behavior of Python's BaseStorage._key() method.
 */
@DisplayName("Key Separator Handling Tests")
class KeySeparatorTest {

  @Test
  @DisplayName("Should avoid double separator when prefix ends with separator")
  void testPrefixEndingWithSeparator() {
    // Issue #368: Prefix ending with separator should not create double separator
    String key = BaseStorage.createKey("123", "user:", ":");
    assertThat(key)
        .isEqualTo("user:123")
        .describedAs("Should strip trailing separator from prefix");

    key = BaseStorage.createKey("456", "app:", ":");
    assertThat(key).isEqualTo("app:456").describedAs("Should strip trailing separator from prefix");
  }

  @Test
  @DisplayName("Should work correctly when prefix does not end with separator")
  void testPrefixWithoutTrailingSeparator() {
    // Normal case: prefix without trailing separator
    String key = BaseStorage.createKey("123", "user", ":");
    assertThat(key).isEqualTo("user:123").describedAs("Should add separator between prefix and id");

    key = BaseStorage.createKey("456", "app", ":");
    assertThat(key).isEqualTo("app:456").describedAs("Should add separator between prefix and id");
  }

  @Test
  @DisplayName("Should handle empty or null prefix")
  void testEmptyPrefix() {
    // Empty prefix should return just the id
    String key = BaseStorage.createKey("123", "", ":");
    assertThat(key).isEqualTo("123").describedAs("Empty prefix should return just the id");

    // Null prefix should return just the id
    key = BaseStorage.createKey("123", null, ":");
    assertThat(key).isEqualTo("123").describedAs("Null prefix should return just the id");
  }

  @Test
  @DisplayName("Should handle different separator characters")
  void testDifferentSeparators() {
    // Test with underscore separator
    String key = BaseStorage.createKey("123", "prefix_", "_");
    assertThat(key)
        .isEqualTo("prefix_123")
        .describedAs("Should strip trailing underscore from prefix");

    key = BaseStorage.createKey("123", "prefix", "_");
    assertThat(key)
        .isEqualTo("prefix_123")
        .describedAs("Should add underscore between prefix and id");

    // Test with dash separator
    key = BaseStorage.createKey("123", "prefix-", "-");
    assertThat(key).isEqualTo("prefix-123").describedAs("Should strip trailing dash from prefix");

    key = BaseStorage.createKey("123", "prefix", "-");
    assertThat(key).isEqualTo("prefix-123").describedAs("Should add dash between prefix and id");
  }

  @Test
  @DisplayName("Should handle multi-level prefixes with trailing separator")
  void testMultiLevelPrefix() {
    // Multi-level prefix ending with separator
    String key = BaseStorage.createKey("123", "app:env:service:", ":");
    assertThat(key)
        .isEqualTo("app:env:service:123")
        .describedAs("Should strip trailing separator from multi-level prefix");

    // Multi-level prefix without trailing separator
    key = BaseStorage.createKey("123", "app:env:service", ":");
    assertThat(key)
        .isEqualTo("app:env:service:123")
        .describedAs("Should add separator to multi-level prefix");
  }

  @Test
  @DisplayName("Should handle null or empty separator")
  void testNullOrEmptySeparator() {
    // Empty separator should concatenate directly
    String key = BaseStorage.createKey("123", "prefix", "");
    assertThat(key)
        .isEqualTo("prefix123")
        .describedAs("Empty separator should concatenate directly");

    // Null separator - current behavior might vary, test for safety
    // The fix should handle this edge case gracefully
    key = BaseStorage.createKey("123", "prefix", null);
    // With null separator, we can't strip trailing separator, so just concatenate
    assertThat(key).isNotNull().describedAs("Should handle null separator gracefully");
  }

  @Test
  @DisplayName("Should handle prefix that is just the separator")
  void testPrefixIsJustSeparator() {
    // Edge case: prefix is just the separator character
    String key = BaseStorage.createKey("123", ":", ":");
    assertThat(key)
        .isEqualTo(":123")
        .describedAs("Prefix of just separator should normalize to separator + id");
  }

  @Test
  @DisplayName("Should handle multiple trailing separators")
  void testMultipleTrailingSeparators() {
    // Edge case: prefix ends with multiple separator characters
    // Note: Python's rstrip() removes all trailing occurrences
    String key = BaseStorage.createKey("123", "prefix:::", ":");
    assertThat(key)
        .isEqualTo("prefix:123")
        .describedAs("Should strip all trailing separators from prefix");
  }
}
