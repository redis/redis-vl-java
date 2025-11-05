package com.redis.vl.langchain4j;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.query.Filter;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for LangChain4JFilterMapper.
 *
 * <p>Tests the mapping from LangChain4J Filter types to RedisVL Filter queries. Based on tests from
 * LangChain4J community PR #183.
 */
class LangChain4JFilterMapperTest {

  @Test
  void testMapNull() {
    // Given: null filter
    // When
    Filter result = LangChain4JFilterMapper.map(null);

    // Then: should return wildcard filter
    assertNotNull(result);
    assertEquals("*", result.build());
  }

  @Test
  void testMapNumericEqual() {
    // Given: numeric equality filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter = metadataKey("age").isEqualTo(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("20"));
  }

  @Test
  void testMapNumericEqualDouble() {
    // Given: numeric equality filter with double
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("price").isEqualTo(19.99);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@price"));
    assertTrue(query.contains("19.99"));
  }

  @Test
  void testMapStringEqual() {
    // Given: string equality filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("name").isEqualTo("Klaus");

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL tag filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("Klaus"));
  }

  @Test
  void testMapNotEqual() {
    // Given: not equal filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter = metadataKey("age").isNotEqualTo(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to negated filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("-") || query.contains("NOT"));
  }

  @Test
  void testMapGreaterThan() {
    // Given: greater than filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter = metadataKey("age").isGreaterThan(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric greater than
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    // RedisVL uses exclusive range (20 for gt, not gte
    assertTrue(query.contains("(20") || query.contains("20"));
  }

  @Test
  void testMapGreaterThanOrEqual() {
    // Given: greater than or equal filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age").isGreaterThanOrEqualTo(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric gte
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("20"));
  }

  @Test
  void testMapLessThan() {
    // Given: less than filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter = metadataKey("age").isLessThan(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric less than
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
  }

  @Test
  void testMapLessThanOrEqual() {
    // Given: less than or equal filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age").isLessThanOrEqualTo(20);

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL numeric lte
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("20"));
  }

  @Test
  void testMapGreaterThanWithString() {
    // Given: greater than with string (invalid)
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("name").isGreaterThan("aaa");

    // When/Then: should throw IllegalArgumentException
    assertThrows(IllegalArgumentException.class, () -> LangChain4JFilterMapper.map(lc4jFilter));
  }

  @Test
  void testMapIsIn() {
    // Given: isIn filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("name").isIn("Klaus", "Martin");

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL tag filter with multiple values
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("Klaus") || query.contains("Martin"));
  }

  @Test
  void testMapIsInSingle() {
    // Given: isIn filter with single value
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter = metadataKey("name").isIn("Klaus");

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL tag filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("Klaus"));
  }

  @Test
  void testMapIsNotIn() {
    // Given: isNotIn filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("name").isNotIn("Klaus", "Martin");

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to negated tag filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("-") || query.contains("NOT"));
  }

  @Test
  void testMapAnd() {
    // Given: AND filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age").isEqualTo(20).and(metadataKey("name").isEqualTo("Klaus"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL AND filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("20"));
    assertTrue(query.contains("Klaus"));
  }

  @Test
  void testMapOr() {
    // Given: OR filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age").isEqualTo(20).or(metadataKey("name").isEqualTo("Klaus"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to RedisVL OR filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age") || query.contains("@name"));
  }

  @Test
  void testMapNot() {
    // Given: NOT filter
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        dev.langchain4j.store.embedding.filter.Filter.not(metadataKey("age").isEqualTo(20));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map to negated filter
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("-") || query.contains("NOT"));
  }

  @Test
  void testMapComplexFilter() {
    // Given: complex filter - age equal 20 AND name equal Klaus AND country in [German, America]
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age")
            .isEqualTo(20)
            .and(metadataKey("name").isEqualTo("Klaus"))
            .and(metadataKey("country").isIn("German", "America"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map correctly
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("@country"));
    assertTrue(query.contains("20"));
    assertTrue(query.contains("Klaus"));
  }

  @Test
  void testMapComplexOrFilter() {
    // Given: age greater than 20 OR name is not in [Klaus, Martin]
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age").isGreaterThan(20).or(metadataKey("name").isNotIn("Klaus", "Martin"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map correctly
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age") || query.contains("@name"));
  }

  @Test
  void testMapChainedAnd() {
    // Given: chained AND - age AND name AND country
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age")
            .isEqualTo(20)
            .and(metadataKey("name").isEqualTo("Klaus"))
            .and(metadataKey("country").isEqualTo("German"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map correctly with all conditions
    assertNotNull(result);
    String query = result.build();
    assertTrue(query.contains("@age"));
    assertTrue(query.contains("@name"));
    assertTrue(query.contains("@country"));
  }

  @Test
  void testMapChainedOr() {
    // Given: chained OR - age OR name OR country
    dev.langchain4j.store.embedding.filter.Filter lc4jFilter =
        metadataKey("age")
            .isEqualTo(20)
            .or(metadataKey("name").isEqualTo("Klaus"))
            .or(metadataKey("country").isEqualTo("German"));

    // When
    Filter result = LangChain4JFilterMapper.map(lc4jFilter);

    // Then: should map correctly
    assertNotNull(result);
    assertNotNull(result.build());
  }
}
