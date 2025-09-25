package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.VectorizerBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for SemanticCache with real LangChain4J embedding models. This test verifies
 * that the SemanticCache works correctly with actual embedding models as used in the Jupyter
 * notebooks.
 */
public class SemanticCacheLangChain4JIntegrationTest extends BaseIntegrationTest {

  private static final String CACHE_NAME = "llmcache";
  private SemanticCache cache;
  private BaseVectorizer vectorizer;

  @BeforeEach
  void setUp() {
    // Clear all Redis data before each test
    unifiedJedis.flushAll();

    try {
      // Create vectorizer using the same approach as the notebook should use
      vectorizer = VectorizerBuilder.local("all-minilm-l6-v2").withDimensions(384).build();

      assertNotNull(vectorizer, "Vectorizer should be created successfully");
      assertEquals("all-minilm-l6-v2", vectorizer.getModelName());
      assertEquals(384, vectorizer.getDimensions());
    } catch (RuntimeException e) {
      // Skip test if the embedding model is not available
      System.err.println("Skipping test - embedding model not available: " + e.getMessage());
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "AllMiniLmL6V2EmbeddingModel not available on classpath");
      return;
    }

    // Initialize semantic cache
    cache =
        new SemanticCache.Builder()
            .name(CACHE_NAME)
            .redisClient(unifiedJedis)
            .vectorizer(vectorizer)
            .distanceThreshold(0.1f)
            .build();
  }

  @Test
  @DisplayName("Basic cache operations with real embedding model")
  void testBasicCacheOperations() {
    if (vectorizer == null) return; // Skip if model not available

    // Test the exact flow from the notebook
    String question = "What is the capital of France?";

    // Check empty cache
    Optional<CacheHit> emptyCheck = cache.check(question);
    assertFalse(emptyCheck.isPresent(), "Cache should be empty initially");

    // Store in cache with metadata
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("city", "Paris");
    metadata.put("country", "france");

    cache.store(question, "Paris", metadata);

    // Check cache again - should find it
    Optional<CacheHit> cacheResponse = cache.check(question);
    assertTrue(cacheResponse.isPresent(), "Should find cached response");

    CacheHit hit = cacheResponse.get();
    assertEquals("What is the capital of France?", hit.getPrompt());
    assertEquals("Paris", hit.getResponse());
    assertEquals(0.0f, hit.getDistance(), 0.001f, "Exact match should have distance 0");
    assertNotNull(hit.getMetadata());
    assertEquals("Paris", hit.getMetadata().get("city"));
    assertEquals("france", hit.getMetadata().get("country"));
  }

  @Test
  @DisplayName("Semantic similarity with real embeddings")
  void testSemanticSimilarity() {
    if (vectorizer == null) return; // Skip if model not available

    // Store a response
    cache.store("What is the capital of France?", "Paris");

    // Check with semantically similar question
    String similarQuestion = "What actually is the capital of France?";
    Optional<CacheHit> similarResponse = cache.check(similarQuestion);

    assertTrue(similarResponse.isPresent(), "Should find semantically similar match");
    assertEquals("Paris", similarResponse.get().getResponse());

    // Distance should be small but not zero (since it's not exact match)
    float distance = similarResponse.get().getDistance();
    assertTrue(distance > 0, "Similar match should have distance > 0");
    assertTrue(distance < 0.1f, "Distance should be below threshold of 0.1");
  }

  @Test
  @DisplayName("Distance threshold adjustment")
  void testDistanceThresholdAdjustment() {
    if (vectorizer == null) return; // Skip if model not available

    cache.store("What is the capital of France?", "Paris");

    // Set very strict threshold
    cache.setDistanceThreshold(0.01f);

    // Similar but not identical question should not match with strict threshold
    Optional<CacheHit> noMatch = cache.check("Tell me the capital of France");
    assertFalse(noMatch.isPresent(), "Should not match with very strict threshold");

    // Widen the threshold
    cache.setDistanceThreshold(0.5f);

    // Now it should match
    Optional<CacheHit> match = cache.check("Tell me the capital of France");
    assertTrue(match.isPresent(), "Should match with wider threshold");
    assertEquals("Paris", match.get().getResponse());
  }

  @Test
  @DisplayName("TTL functionality with real model")
  void testTTLFunctionality() throws InterruptedException {
    if (vectorizer == null) return; // Skip if model not available

    // Create a cache with 2 second TTL
    SemanticCache ttlCache =
        new SemanticCache.Builder()
            .name("llmcache_ttl")
            .redisClient(unifiedJedis)
            .vectorizer(vectorizer)
            .distanceThreshold(0.1f)
            .ttl(2) // 2 seconds
            .build();

    // Store an entry
    ttlCache.store("This is a TTL test", "This is a TTL test response");

    // Should exist immediately
    Optional<CacheHit> immediate = ttlCache.check("This is a TTL test");
    assertTrue(immediate.isPresent(), "Should exist immediately after storing");

    // Wait for expiry
    Thread.sleep(3000);

    // Should be expired now
    Optional<CacheHit> expired = ttlCache.check("This is a TTL test");
    assertFalse(expired.isPresent(), "Should be expired after TTL");

    // Clean up
    ttlCache.clear();
  }

  @Test
  @DisplayName("Multiple users with metadata filtering")
  void testMultipleUsersWithMetadata() {
    if (vectorizer == null) return; // Skip if model not available

    // Store entries with user metadata
    Map<String, Object> userAbc = new HashMap<>();
    userAbc.put("user", "abc");

    Map<String, Object> userDef = new HashMap<>();
    userDef.put("user", "def");

    cache.store(
        "What is the phone number linked to my account?",
        "The number on file is 123-555-0000",
        userAbc);

    cache.store(
        "What's the phone number linked in my account?",
        "The number on file is 123-555-1111",
        userDef);

    // Check without filter - should return one of them
    Optional<CacheHit> phoneResponse =
        cache.check("What is the phone number linked to my account?");

    assertTrue(phoneResponse.isPresent(), "Should find a phone number entry");
    String response = phoneResponse.get().getResponse();
    assertTrue(
        response.contains("123-555-0000") || response.contains("123-555-1111"),
        "Should return one of the phone numbers");
  }

  @Test
  @DisplayName("Embedding generation verification")
  void testEmbeddingGeneration() {
    if (vectorizer == null) return; // Skip if model not available

    // Test that the vectorizer can generate embeddings without error
    String testText = "What is the capital of France?";

    float[] embedding = vectorizer.embed(testText);
    assertNotNull(embedding, "Embedding should not be null");
    assertEquals(384, embedding.length, "Embedding should have 384 dimensions");

    // Verify it's a valid embedding (not all zeros)
    boolean hasNonZero = false;
    for (float value : embedding) {
      if (value != 0.0f) {
        hasNonZero = true;
        break;
      }
    }
    assertTrue(hasNonZero, "Embedding should contain non-zero values");

    // Test that embeddings are consistent
    float[] embedding2 = vectorizer.embed(testText);
    assertArrayEquals(embedding, embedding2, "Same text should produce same embedding");
  }

  @Test
  @DisplayName("Cache statistics")
  void testCacheStatistics() {
    if (vectorizer == null) return; // Skip if model not available

    // Initial state
    assertEquals(0, cache.getHitCount(), "Initial hit count should be 0");
    assertEquals(0, cache.getMissCount(), "Initial miss count should be 0");
    assertEquals(0.0f, cache.getHitRate(), 0.001f, "Initial hit rate should be 0");

    // Store some entries
    cache.store("Question 1", "Answer 1");
    cache.store("Question 2", "Answer 2");

    // Some misses
    cache.check("Non-existent question 1");
    cache.check("Non-existent question 2");
    assertEquals(0, cache.getHitCount(), "Hit count should still be 0");
    assertEquals(2, cache.getMissCount(), "Miss count should be 2");

    // Some hits
    cache.check("Question 1");
    cache.check("Question 2");
    cache.check("Question 1"); // Second hit on same question
    assertEquals(3, cache.getHitCount(), "Hit count should be 3");
    assertEquals(2, cache.getMissCount(), "Miss count should still be 2");

    // Check hit rate
    float expectedHitRate = 3.0f / 5.0f; // 3 hits out of 5 total checks
    assertEquals(expectedHitRate, cache.getHitRate(), 0.001f, "Hit rate should be 60%");
  }
}
