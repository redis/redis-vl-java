package com.redis.vl.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Test that VectorQuery correctly handles JSONPath field names */
public class VectorQueryJsonPathTest {

  @Test
  public void testVectorQueryEscapesJsonPath() {
    // Test that VectorQuery.toQueryString() correctly escapes JSONPath syntax for RediSearch
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
            .field("$.product_embedding") // JSONPath field name
            .numResults(5)
            .build();

    String queryString = query.toQueryString();

    // The query string should escape the JSONPath for RediSearch
    assertTrue(
        queryString.contains("@\\$\\.product_embedding"),
        "Query should escape JSONPath for RediSearch. Got: " + queryString);
    assertFalse(
        queryString.contains("@$.product_embedding"),
        "Query should not contain unescaped JSONPath syntax. Got: " + queryString);
  }

  @Test
  public void testVectorQueryWithPlainFieldName() {
    // Test that VectorQuery works correctly with plain field names too
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
            .field("product_embedding") // Plain field name
            .numResults(5)
            .build();

    String queryString = query.toQueryString();

    // The query string should use the plain field name
    assertTrue(
        queryString.contains("@product_embedding"),
        "Query should use plain field name. Got: " + queryString);
  }

  @Test
  public void testVectorQueryWithNestedJsonPath() {
    // Test that nested JSONPath is correctly escaped
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
            .field("$.embeddings.product") // Nested JSONPath
            .numResults(5)
            .build();

    String queryString = query.toQueryString();

    // Should escape the entire JSONPath including dots
    assertTrue(
        queryString.contains("@\\$\\.embeddings\\.product"),
        "Query should escape full nested JSONPath. Got: " + queryString);
    assertFalse(
        queryString.contains("@$.embeddings.product"),
        "Query should not contain unescaped JSONPath syntax. Got: " + queryString);
  }

  @Test
  public void testVectorQueryPreservesFilterJsonPath() {
    // Test that both vector and filter fields correctly escape JSONPath
    Filter filter = Filter.text("$.description", "laptop");

    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
            .field("$.product_embedding")
            .withPreFilter(filter.build())
            .numResults(5)
            .build();

    String queryString = query.toQueryString();

    // Both vector and filter fields should be properly escaped
    assertTrue(
        queryString.contains("@\\$\\.product_embedding"),
        "Vector field should be escaped. Got: " + queryString);
    assertTrue(
        queryString.contains("@\\$\\.description"),
        "Filter field should be escaped. Got: " + queryString);
  }
}
