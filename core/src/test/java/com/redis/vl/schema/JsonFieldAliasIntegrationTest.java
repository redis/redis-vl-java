package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.Filter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.SearchResult;

/**
 * Integration test to verify that IndexSchema.fromDict() properly creates field aliases for JSON
 * storage.
 *
 * <p>This test validates that when using JSON storage with field definitions like: {@code
 * Map.of("name", "$.category", "type", "tag", "attrs", Map.of("as", "category"))} the resulting
 * Redis index correctly supports querying by the alias name "category" instead of the full JSON
 * path "$.category".
 */
@Tag("integration")
public class JsonFieldAliasIntegrationTest {

  private JedisPooled jedis;
  private SearchIndex searchIndex;
  private static final String INDEX_NAME = "test_json_alias";

  @BeforeEach
  void setUp() {
    jedis = new JedisPooled("localhost", 6379);

    // Create schema with JSON storage and field aliases
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of("name", INDEX_NAME, "prefix", INDEX_NAME + ":", "storage_type", "json"),
            "fields",
            List.of(
                Map.of("name", "$.text", "type", "text", "attrs", Map.of("as", "text")),
                // Tag field with alias
                Map.of("name", "$.category", "type", "tag", "attrs", Map.of("as", "category")),
                // Numeric field with alias
                Map.of("name", "$.year", "type", "numeric", "attrs", Map.of("as", "year")),
                // Vector field
                Map.of(
                    "name",
                    "$.embedding",
                    "type",
                    "vector",
                    "attrs",
                    Map.of(
                        "dims",
                        3,
                        "algorithm",
                        "flat",
                        "distance_metric",
                        "cosine",
                        "as",
                        "embedding"))));

    searchIndex = new SearchIndex(IndexSchema.fromDict(schema), jedis);
    searchIndex.create(true); // Overwrite if exists
  }

  @AfterEach
  void tearDown() {
    if (searchIndex != null) {
      try {
        searchIndex.clear();
        searchIndex.drop();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
    if (jedis != null) {
      jedis.close();
    }
  }

  @Test
  void testTagFieldAliasWorks() {
    // Given - Load test documents with JSON storage
    List<Map<String, Object>> documents =
        List.of(
            Map.of(
                "id", "1",
                "text", "Tech article",
                "category", "tech",
                "year", 2023,
                "embedding", new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "id", "2",
                "text", "Science article",
                "category", "science",
                "year", 2024,
                "embedding", new float[] {0.4f, 0.5f, 0.6f}),
            Map.of(
                "id", "3",
                "text", "Tech news",
                "category", "tech",
                "year", 2024,
                "embedding", new float[] {0.7f, 0.8f, 0.9f}));

    searchIndex.load(documents, "id");

    // Wait for indexing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // When - Query using the ALIAS name (category), not the JSON path ($.category)
    Filter filter = Filter.tag("category", "tech");
    SearchResult result = searchIndex.search(filter.build());

    // Then - Should find 2 documents with category="tech"
    assertThat(result.getTotalResults())
        .as("Should find documents using alias 'category'")
        .isEqualTo(2);
  }

  @Test
  void testNumericFieldAliasWorks() {
    // Given
    List<Map<String, Object>> documents =
        List.of(
            Map.of(
                "id", "1",
                "text", "Old article",
                "category", "tech",
                "year", 2020,
                "embedding", new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "id", "2",
                "text", "Recent article",
                "category", "tech",
                "year", 2023,
                "embedding", new float[] {0.4f, 0.5f, 0.6f}));

    searchIndex.load(documents, "id");

    // Wait for indexing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // When - Query using the ALIAS name (year)
    Filter filter = Filter.numeric("year").gte(2023);
    SearchResult result = searchIndex.search(filter.build());

    // Then
    assertThat(result.getTotalResults())
        .as("Should find documents using alias 'year'")
        .isEqualTo(1);
  }

  @Test
  void testCombinedFiltersWithAliases() {
    // Given
    List<Map<String, Object>> documents =
        List.of(
            Map.of(
                "id", "1",
                "text", "Old tech",
                "category", "tech",
                "year", 2020,
                "embedding", new float[] {0.1f, 0.2f, 0.3f}),
            Map.of(
                "id", "2",
                "text", "Recent tech",
                "category", "tech",
                "year", 2024,
                "embedding", new float[] {0.4f, 0.5f, 0.6f}),
            Map.of(
                "id", "3",
                "text", "Recent science",
                "category", "science",
                "year", 2024,
                "embedding", new float[] {0.7f, 0.8f, 0.9f}));

    searchIndex.load(documents, "id");

    // Wait for indexing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // When - Combined filter: category=tech AND year>=2024
    Filter categoryFilter = Filter.tag("category", "tech");
    Filter yearFilter = Filter.numeric("year").gte(2024);
    Filter combinedFilter = Filter.and(categoryFilter, yearFilter);

    SearchResult result = searchIndex.search(combinedFilter.build());

    // Then
    assertThat(result.getTotalResults())
        .as("Should find documents matching both filters using aliases")
        .isEqualTo(1);
  }
}
