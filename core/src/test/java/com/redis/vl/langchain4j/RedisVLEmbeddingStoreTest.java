package com.redis.vl.langchain4j;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

/**
 * Test for RedisVLEmbeddingStore - LangChain4J integration.
 *
 * <p>Tests the EmbeddingStore implementation using RedisVL as backend.
 */
@Tag("integration")
class RedisVLEmbeddingStoreTest {

  private JedisPooled jedis;
  private SearchIndex searchIndex;
  private RedisVLEmbeddingStore embeddingStore;
  private static final String INDEX_NAME = "test_lc4j_embeddings";
  private static final int VECTOR_DIM = 384; // All-MiniLM-L6-v2 dimensions

  @BeforeEach
  void setUp() {
    jedis = new JedisPooled("localhost", 6379);

    // Create schema for embeddings
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of("name", INDEX_NAME, "prefix", INDEX_NAME + ":", "storage_type", "hash"),
            "fields",
            List.of(
                Map.of("name", "text", "type", "text"),
                Map.of("name", "metadata", "type", "text"), // JSON string
                Map.of(
                    "name",
                    "vector",
                    "type",
                    "vector",
                    "attrs",
                    Map.of("dims", VECTOR_DIM, "algorithm", "flat", "distance_metric", "cosine"))));

    // Create search index
    searchIndex = new SearchIndex(IndexSchema.fromDict(schema), jedis);
    try {
      searchIndex.create(true); // Overwrite if exists
    } catch (Exception e) {
      // Index might exist, that's ok
    }

    // Create embedding store
    embeddingStore = new RedisVLEmbeddingStore(searchIndex);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data - use removeAll() to delete all embeddings
    if (embeddingStore != null) {
      try {
        embeddingStore.removeAll();
      } catch (Exception e) {
        // Ignore if no data exists
      }
    }

    // Drop the index
    if (searchIndex != null) {
      try {
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
  void testAddSingleEmbedding() {
    // Given
    Embedding embedding = createTestEmbedding(1.0f, 2.0f, 3.0f);

    // When
    String id = embeddingStore.add(embedding);

    // Then
    assertNotNull(id);
    assertFalse(id.isEmpty());
  }

  @Test
  void testAddEmbeddingWithTextSegment() {
    // Given
    Embedding embedding = createTestEmbedding(1.0f, 2.0f, 3.0f);
    TextSegment textSegment = TextSegment.from("Hello world");

    // When
    String id = embeddingStore.add(embedding, textSegment);

    // Then
    assertNotNull(id);
    assertFalse(id.isEmpty());
  }

  @Test
  void testAddEmbeddingWithMetadata() {
    // Given
    Embedding embedding = createTestEmbedding(1.0f, 2.0f, 3.0f);
    Metadata metadata = new Metadata();
    metadata.put("author", "John Doe");
    metadata.put("category", "test");
    TextSegment textSegment = TextSegment.from("Test content", metadata);

    // When
    String id = embeddingStore.add(embedding, textSegment);

    // Then
    assertNotNull(id);

    // Verify we can retrieve with metadata
    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(embedding, 1, 0.0);
    assertEquals(1, matches.size());
    TextSegment retrieved = matches.get(0).embedded();
    assertEquals("Test content", retrieved.text());
    assertEquals("John Doe", retrieved.metadata().getString("author"));
    assertEquals("test", retrieved.metadata().getString("category"));
  }

  @Test
  void testAddAll() {
    // Given
    List<Embedding> embeddings =
        List.of(
            createTestEmbedding(1.0f, 0.0f, 0.0f),
            createTestEmbedding(0.0f, 1.0f, 0.0f),
            createTestEmbedding(0.0f, 0.0f, 1.0f));

    List<TextSegment> segments =
        List.of(
            TextSegment.from("First document"),
            TextSegment.from("Second document"),
            TextSegment.from("Third document"));

    // When
    List<String> ids = embeddingStore.addAll(embeddings, segments);

    // Then
    assertEquals(3, ids.size());
    assertTrue(ids.stream().allMatch(id -> id != null && !id.isEmpty()));
  }

  @Test
  void testFindRelevant() {
    // Given - Add some test documents
    Embedding targetEmbedding = createTestEmbedding(1.0f, 0.0f, 0.0f);
    embeddingStore.add(targetEmbedding, TextSegment.from("Target document"));

    Embedding similarEmbedding = createTestEmbedding(0.9f, 0.1f, 0.0f);
    embeddingStore.add(similarEmbedding, TextSegment.from("Similar document"));

    Embedding differentEmbedding = createTestEmbedding(0.0f, 0.0f, 1.0f);
    embeddingStore.add(differentEmbedding, TextSegment.from("Different document"));

    // When - Search for target
    Embedding queryEmbedding = createTestEmbedding(1.0f, 0.0f, 0.0f);
    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 2);

    // Then
    assertEquals(2, matches.size());

    // First match should be exact or very close
    EmbeddingMatch<TextSegment> bestMatch = matches.get(0);
    assertTrue(bestMatch.score() > 0.9); // Cosine similarity close to 1.0
    assertEquals("Target document", bestMatch.embedded().text());

    // Second match should be the similar one
    // Note: Cosine similarity between (1,0,0) and (0.9,0.1,0) is ~0.994, very high!
    EmbeddingMatch<TextSegment> secondMatch = matches.get(1);
    assertTrue(secondMatch.score() > 0.99);
    assertEquals("Similar document", secondMatch.embedded().text());
  }

  @Test
  void testFindRelevantWithMinScore() {
    // Given
    Embedding targetEmbedding = createTestEmbedding(1.0f, 0.0f, 0.0f);
    embeddingStore.add(targetEmbedding, TextSegment.from("Target"));

    Embedding differentEmbedding = createTestEmbedding(0.0f, 1.0f, 0.0f);
    embeddingStore.add(differentEmbedding, TextSegment.from("Different"));

    // When - Search with high minimum score
    Embedding queryEmbedding = createTestEmbedding(1.0f, 0.0f, 0.0f);
    List<EmbeddingMatch<TextSegment>> matches =
        embeddingStore.findRelevant(queryEmbedding, 10, 0.95);

    // Then - Only exact match should be returned
    assertEquals(1, matches.size());
    assertEquals("Target", matches.get(0).embedded().text());
  }

  @Test
  void testScoreConversion() {
    // Given - Add a document
    Embedding embedding = createTestEmbedding(1.0f, 0.0f, 0.0f);
    embeddingStore.add(embedding, TextSegment.from("Test"));

    // When - Search with exact same embedding
    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(embedding, 1);

    // Then - Score should be close to 1.0 (perfect match)
    assertEquals(1, matches.size());
    double score = matches.get(0).score();
    assertTrue(
        score > 0.99 && score <= 1.0, "Expected score close to 1.0 for exact match, got: " + score);
  }

  @Test
  void testEmptyResults() {
    // Given - Empty store
    Embedding queryEmbedding = createTestEmbedding(1.0f, 0.0f, 0.0f);

    // When
    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 10);

    // Then
    assertTrue(matches.isEmpty());
  }

  @Test
  void testAddNullEmbedding() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> embeddingStore.add(null));
  }

  @Test
  void testAddNullTextSegment() {
    // Given
    Embedding embedding = createTestEmbedding(1.0f, 0.0f, 0.0f);

    // When - Text segment can be null (embedding only)
    String id = embeddingStore.add(embedding, null);

    // Then
    assertNotNull(id);
  }

  // Note: Filter-based search tests require schema to be configured with indexed metadata fields.
  // For filter support, create schema with TAG/NUMERIC fields for each filterable metadata field.
  // Example: Map.of("name", "category", "type", "tag") or Map.of("name", "rating", "type",
  // "numeric")
  // See LangChain4JFilterMapper and LangChain4JFilterMapperTest for filter mapping implementation.

  @Test
  void testRemoveAllByIds() {
    // Given - Add some documents
    String id1 = embeddingStore.add(createTestEmbedding(1.0f, 0.0f, 0.0f));
    String id2 = embeddingStore.add(createTestEmbedding(0.0f, 1.0f, 0.0f));
    String id3 = embeddingStore.add(createTestEmbedding(0.0f, 0.0f, 1.0f));

    // When - Remove two of them
    embeddingStore.removeAll(List.of(id1, id2));

    // Then - Only one should remain
    List<EmbeddingMatch<TextSegment>> matches =
        embeddingStore.findRelevant(createTestEmbedding(1.0f, 0.0f, 0.0f), 10);
    assertEquals(1, matches.size());
  }

  // Note: removeAll(Filter) test also requires indexed metadata fields in schema.
  // See comment above for filter configuration requirements.

  @Test
  void testRemoveAllEmpty() {
    // Given - Add a document
    embeddingStore.add(createTestEmbedding(1.0f, 0.0f, 0.0f));

    // When - Remove all
    embeddingStore.removeAll();

    // Then - Store should be empty
    List<EmbeddingMatch<TextSegment>> matches =
        embeddingStore.findRelevant(createTestEmbedding(1.0f, 0.0f, 0.0f), 10);
    assertTrue(matches.isEmpty());
  }

  /**
   * Helper to create test embeddings with padding to match VECTOR_DIM.
   *
   * @param values Initial values for the vector
   * @return Embedding with VECTOR_DIM dimensions
   */
  private Embedding createTestEmbedding(float... values) {
    float[] vector = new float[VECTOR_DIM];
    System.arraycopy(values, 0, vector, 0, Math.min(values.length, VECTOR_DIM));
    // Fill remaining with zeros for deterministic tests
    // (random values would dominate cosine similarity in high dimensions)
    for (int i = values.length; i < VECTOR_DIM; i++) {
      vector[i] = 0.0f;
    }
    return new Embedding(vector);
  }
}
