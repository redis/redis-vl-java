package com.redis.vl.langchain4j;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

/**
 * Integration tests for RedisVLEmbeddingStore with filter support.
 *
 * <p>These tests verify filter functionality with properly configured metadata field indexes. Based
 * on tests from LangChain4J community PR #183.
 */
@Tag("integration")
class RedisVLEmbeddingStoreFilterTest {

  private JedisPooled jedis;
  private SearchIndex searchIndex;
  private RedisVLEmbeddingStore embeddingStore;
  private static final String INDEX_NAME = "test_lc4j_filters";
  private static final int VECTOR_DIM = 384;

  @BeforeEach
  void setUp() {
    jedis = new JedisPooled("localhost", 6379);

    // Create schema with indexed metadata fields for filtering
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of("name", INDEX_NAME, "prefix", INDEX_NAME + ":", "storage_type", "json"),
            "fields",
            List.of(
                // Text and vector fields
                Map.of("name", "text", "type", "text"),
                Map.of("name", "metadata", "type", "text"),
                Map.of(
                    "name",
                    "vector",
                    "type",
                    "vector",
                    "attrs",
                    Map.of(
                        "dims",
                        VECTOR_DIM,
                        "algorithm",
                        "flat",
                        "distance_metric",
                        "cosine",
                        "as",
                        "vector")),
                // Indexed metadata fields for filtering
                Map.of("name", "$.category", "type", "tag", "attrs", Map.of("as", "category")),
                Map.of("name", "$.year", "type", "numeric", "attrs", Map.of("as", "year")),
                Map.of("name", "$.rating", "type", "numeric", "attrs", Map.of("as", "rating")),
                Map.of("name", "$.price", "type", "numeric", "attrs", Map.of("as", "price")),
                Map.of("name", "$.status", "type", "tag", "attrs", Map.of("as", "status")),
                Map.of(
                    "name",
                    "$.tags",
                    "type",
                    "tag",
                    "attrs",
                    Map.of("as", "tags", "separator", "|"))));

    searchIndex = new SearchIndex(IndexSchema.fromDict(schema), jedis);
    try {
      searchIndex.create(true);
    } catch (Exception e) {
      // Index might exist
    }

    embeddingStore = new RedisVLEmbeddingStore(searchIndex);
  }

  @AfterEach
  void tearDown() {
    if (embeddingStore != null) {
      try {
        embeddingStore.removeAll();
      } catch (Exception e) {
        // Ignore
      }
    }

    if (searchIndex != null) {
      try {
        searchIndex.drop();
      } catch (Exception e) {
        // Ignore
      }
    }

    if (jedis != null) {
      jedis.close();
    }
  }

  @Test
  void testFilterByTagEqualTo() {
    // Given
    Metadata tech1 = new Metadata().put("category", "tech");
    Embedding emb1 = createEmbedding(1.0f, 0.0f);
    embeddingStore.add(emb1, TextSegment.from("Tech article 1", tech1));

    Metadata tech2 = new Metadata().put("category", "tech");
    Embedding emb2 = createEmbedding(0.9f, 0.1f);
    embeddingStore.add(emb2, TextSegment.from("Tech article 2", tech2));

    Metadata science = new Metadata().put("category", "science");
    Embedding emb3 = createEmbedding(0.8f, 0.2f);
    embeddingStore.add(emb3, TextSegment.from("Science article", science));

    waitForIndexing();

    // When
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category").isEqualTo("tech");
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(emb1)
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(2, result.matches().size());
    for (var match : result.matches()) {
      assertEquals("tech", match.embedded().metadata().getString("category"));
    }
  }

  @Test
  void testFilterByNumericGreaterThan() {
    // Given
    Metadata old = new Metadata().put("year", 2020);
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Old", old));

    Metadata recent1 = new Metadata().put("year", 2023);
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Recent 1", recent1));

    Metadata recent2 = new Metadata().put("year", 2024);
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Recent 2", recent2));

    // When - Filter for year > 2022
    dev.langchain4j.store.embedding.filter.Filter filter = metadataKey("year").isGreaterThan(2022);
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(2, result.matches().size());
    for (var match : result.matches()) {
      int year = match.embedded().metadata().getInteger("year");
      assertTrue(year > 2022);
    }
  }

  @Test
  void testFilterByNumericGreaterThanOrEqualTo() {
    // Given
    Metadata low = new Metadata().put("rating", 3.5);
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Low rated", low));

    Metadata high1 = new Metadata().put("rating", 4.0);
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("High 1", high1));

    Metadata high2 = new Metadata().put("rating", 4.5);
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("High 2", high2));

    // When - Filter for rating >= 4.0
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("rating").isGreaterThanOrEqualTo(4.0);
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(2, result.matches().size());
    for (var match : result.matches()) {
      double rating = match.embedded().metadata().getDouble("rating");
      assertTrue(rating >= 4.0);
    }
  }

  @Test
  void testFilterByNumericLessThan() {
    // Given
    Metadata cheap = new Metadata().put("price", 10.0);
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Cheap", cheap));

    Metadata expensive1 = new Metadata().put("price", 100.0);
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Expensive 1", expensive1));

    Metadata expensive2 = new Metadata().put("price", 200.0);
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Expensive 2", expensive2));

    // When - Filter for price < 50
    dev.langchain4j.store.embedding.filter.Filter filter = metadataKey("price").isLessThan(50.0);
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(1, result.matches().size());
    assertTrue(result.matches().get(0).embedded().metadata().getDouble("price") < 50.0);
  }

  @Test
  void testFilterByTagIsIn() {
    // Given
    Metadata tech = new Metadata().put("category", "tech");
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Tech", tech));

    Metadata science = new Metadata().put("category", "science");
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Science", science));

    Metadata health = new Metadata().put("category", "health");
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Health", health));

    // When - Filter for category in [tech, science]
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category").isIn("tech", "science");
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(2, result.matches().size());
    for (var match : result.matches()) {
      String cat = match.embedded().metadata().getString("category");
      assertTrue(cat.equals("tech") || cat.equals("science"));
    }
  }

  @Test
  void testFilterWithAnd() {
    // Given
    Metadata match = new Metadata().put("category", "tech").put("year", 2023);
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Match", match));

    Metadata wrongCat = new Metadata().put("category", "science").put("year", 2023);
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Wrong cat", wrongCat));

    Metadata wrongYear = new Metadata().put("category", "tech").put("year", 2020);
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Wrong year", wrongYear));

    // When - Filter for category=tech AND year=2023
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category").isEqualTo("tech").and(metadataKey("year").isEqualTo(2023));
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(1, result.matches().size());
    Metadata resultMeta = result.matches().get(0).embedded().metadata();
    assertEquals("tech", resultMeta.getString("category"));
    assertEquals(2023, resultMeta.getInteger("year").intValue());
  }

  @Test
  void testFilterWithOr() {
    // Given
    Metadata tech = new Metadata().put("category", "tech");
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Tech", tech));

    Metadata science = new Metadata().put("category", "science");
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Science", science));

    Metadata health = new Metadata().put("category", "health");
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Health", health));

    // When - Filter for category=tech OR category=science
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category").isEqualTo("tech").or(metadataKey("category").isEqualTo("science"));
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(2, result.matches().size());
    for (var match : result.matches()) {
      String cat = match.embedded().metadata().getString("category");
      assertTrue(cat.equals("tech") || cat.equals("science"));
    }
  }

  @Test
  void testFilterWithNot() {
    // Given
    Metadata tech = new Metadata().put("category", "tech");
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Tech", tech));

    Metadata science = new Metadata().put("category", "science");
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Science", science));

    // When - Filter for NOT category=tech
    dev.langchain4j.store.embedding.filter.Filter filter =
        dev.langchain4j.store.embedding.filter.Filter.not(
            metadataKey("category").isEqualTo("tech"));
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(1, result.matches().size());
    assertNotEquals("tech", result.matches().get(0).embedded().metadata().getString("category"));
  }

  @Test
  void testComplexFilter() {
    // Given - Add various documents
    Metadata match = new Metadata().put("category", "tech").put("year", 2023).put("rating", 4.5);
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Match", match));

    Metadata lowRating =
        new Metadata().put("category", "tech").put("year", 2023).put("rating", 3.0);
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Low rating", lowRating));

    Metadata oldYear = new Metadata().put("category", "tech").put("year", 2020).put("rating", 4.5);
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Old year", oldYear));

    Metadata wrongCat =
        new Metadata().put("category", "science").put("year", 2023).put("rating", 4.5);
    embeddingStore.add(createEmbedding(0.7f, 0.3f), TextSegment.from("Wrong cat", wrongCat));

    // When - Complex filter: category=tech AND year=2023 AND rating >= 4.0
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category")
            .isEqualTo("tech")
            .and(metadataKey("year").isEqualTo(2023))
            .and(metadataKey("rating").isGreaterThanOrEqualTo(4.0));

    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(createEmbedding(1.0f, 0.0f))
            .maxResults(10)
            .minScore(0.0)
            .filter(filter)
            .build();

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

    // Then
    assertEquals(1, result.matches().size());
    Metadata resultMeta = result.matches().get(0).embedded().metadata();
    assertEquals("tech", resultMeta.getString("category"));
    assertEquals(2023, resultMeta.getInteger("year").intValue());
    assertTrue(resultMeta.getDouble("rating") >= 4.0);
  }

  @Test
  void testRemoveAllWithFilter() {
    // Given
    Metadata tech1 = new Metadata().put("category", "tech");
    embeddingStore.add(createEmbedding(1.0f, 0.0f), TextSegment.from("Tech 1", tech1));

    Metadata tech2 = new Metadata().put("category", "tech");
    embeddingStore.add(createEmbedding(0.9f, 0.1f), TextSegment.from("Tech 2", tech2));

    Metadata science = new Metadata().put("category", "science");
    embeddingStore.add(createEmbedding(0.8f, 0.2f), TextSegment.from("Science", science));

    // When - Remove all tech documents
    dev.langchain4j.store.embedding.filter.Filter filter =
        metadataKey("category").isEqualTo("tech");
    embeddingStore.removeAll(filter);

    // Then - Only science should remain
    List<EmbeddingMatch<TextSegment>> matches =
        embeddingStore.findRelevant(createEmbedding(1.0f, 0.0f), 10);
    assertEquals(1, matches.size());
    assertEquals("science", matches.get(0).embedded().metadata().getString("category"));
  }

  private Embedding createEmbedding(float... values) {
    float[] vector = new float[VECTOR_DIM];
    System.arraycopy(values, 0, vector, 0, Math.min(values.length, VECTOR_DIM));
    for (int i = values.length; i < VECTOR_DIM; i++) {
      vector[i] = 0.0f;
    }
    return new Embedding(vector);
  }

  private void waitForIndexing() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
