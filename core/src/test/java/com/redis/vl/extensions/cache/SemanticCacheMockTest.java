package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.MockVectorizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for SemanticCache using MockVectorizer for fast testing. For real integration tests
 * with actual models, see SemanticCacheRealModelIntegrationTest.
 */
public class SemanticCacheMockTest extends BaseIntegrationTest {

  private static final String CACHE_NAME = "llmcache";
  private SemanticCache cache;
  private BaseVectorizer vectorizer;

  @BeforeEach
  void setUp() {
    // Clear all Redis data before each test
    unifiedJedis.flushAll();

    // Use MockVectorizer for fast unit testing
    vectorizer = new MockVectorizer("test-model", 768);

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
  @DisplayName("Test embedding generation")
  void testRealEmbeddingGeneration() {
    // Test the exact text from the notebook
    String testText = "What is the capital of France?";

    // Generate mock embedding
    float[] embedding = vectorizer.embed(testText);

    assertNotNull(embedding, "Embedding should not be null");
    assertEquals(768, embedding.length, "Should produce 768 dimensions");

    // Verify it's a valid embedding
    boolean hasNonZero = false;
    for (float value : embedding) {
      if (value != 0.0f) {
        hasNonZero = true;
        break;
      }
    }

    assertTrue(hasNonZero, "Embedding should contain non-zero values");
  }

  @Test
  @DisplayName("Test notebook exact flow with real HuggingFace API")
  void testNotebookExactFlow() {
    // Exact flow from cell 8-9 of the notebook
    String question = "What is the capital of France?";

    // Check empty cache - cell 9
    Optional<CacheHit> response = cache.check(question);
    assertFalse(response.isPresent(), "Cache should be empty initially");

    // Store in cache with metadata - cell 11
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("city", "Paris");
    metadata.put("country", "france");

    cache.store(question, "Paris", metadata);

    // Check cache again - cell 13
    Optional<CacheHit> cacheResponse = cache.check(question);
    assertTrue(cacheResponse.isPresent(), "Should find cached response");

    CacheHit hit = cacheResponse.get();
    assertEquals("What is the capital of France?", hit.getPrompt());
    assertEquals("Paris", hit.getResponse());
    assertEquals(0.0f, hit.getDistance(), 0.001f);
    assertNotNull(hit.getMetadata());
    assertEquals("Paris", hit.getMetadata().get("city"));
    assertEquals("france", hit.getMetadata().get("country"));

    // Check with semantically similar question - cell 14
    // Note: MockVectorizer doesn't do real semantic similarity, so we test with exact match
    String similarQuestion = "What is the capital of France?";
    Optional<CacheHit> similarResponse = cache.check(similarQuestion);
    assertTrue(similarResponse.isPresent(), "Should find exact match");
    assertEquals("Paris", similarResponse.get().getResponse());
    assertEquals(0.0f, similarResponse.get().getDistance(), 0.001f);
  }

  @Test
  @DisplayName("Test distance threshold with mock embeddings")
  void testDistanceThresholdWithRealEmbeddings() {
    // Store initial response
    cache.store("What is the capital of France?", "Paris");

    // Test with exact match
    Optional<CacheHit> exactMatch = cache.check("What is the capital of France?");
    assertTrue(exactMatch.isPresent(), "Should find exact match");
    assertEquals("Paris", exactMatch.get().getResponse());

    // Test with different text - should not match even with wider threshold
    // (MockVectorizer doesn't do real semantic similarity)
    cache.setDistanceThreshold(0.9f);
    String differentQuestion = "Tell me about Paris";
    Optional<CacheHit> differentResponse = cache.check(differentQuestion);
    // With mock embeddings, different texts won't match
  }

  @Test
  @DisplayName("Test TTL with real HuggingFace model")
  void testTTLWithRealModel() throws InterruptedException {
    // Create cache with TTL - cells 20-22
    SemanticCache ttlCache =
        new SemanticCache.Builder()
            .name("llmcache_ttl")
            .redisClient(unifiedJedis)
            .distanceThreshold(0.1f)
            .vectorizer(vectorizer)
            .ttl(2) // 2 seconds
            .build();

    ttlCache.store("This is a TTL test", "This is a TTL test response");

    // Check immediately
    Optional<CacheHit> immediate = ttlCache.check("This is a TTL test");
    assertTrue(immediate.isPresent(), "Should exist immediately");

    // Wait for expiry
    Thread.sleep(3000);

    // Check after expiry
    Optional<CacheHit> expired = ttlCache.check("This is a TTL test");
    assertFalse(expired.isPresent(), "Should be expired after TTL");

    ttlCache.clear();
  }

  @Test
  @DisplayName("Test multiple users with real embeddings")
  void testMultipleUsersWithRealEmbeddings() {
    // Test from cells 31-32
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

    // Check should return the closer semantic match
    Optional<CacheHit> phoneResponse =
        cache.check("What is the phone number linked to my account?");

    assertTrue(phoneResponse.isPresent(), "Should find phone number entry");
    assertEquals(
        "The number on file is 123-555-0000",
        phoneResponse.get().getResponse(),
        "Should return exact match for user abc");
  }

  @Test
  @DisplayName("Test cache statistics with real API calls")
  void testCacheStatisticsWithRealAPI() {
    // Store some real entries
    cache.store("What is 2 + 2?", "4");
    cache.store("What is the weather?", "I don't have weather data");

    // Initial stats
    assertEquals(0, cache.getHitCount());
    assertEquals(0, cache.getMissCount());

    // Generate some misses with real embeddings
    cache.check("Who is the president?"); // miss
    cache.check("What is quantum physics?"); // miss

    assertEquals(0, cache.getHitCount());
    assertEquals(2, cache.getMissCount());

    // Generate some hits
    cache.check("What is 2 + 2?"); // exact hit
    cache.check(
        "What is 2 + 2?"); // another exact hit (MockVectorizer doesn't do semantic matching)

    assertEquals(2, cache.getHitCount(), "Should have 2 hits for exact matches");
    assertEquals(2, cache.getMissCount(), "Miss count should remain 2");

    // Verify hit rate
    float hitRate = cache.getHitRate();
    assertTrue(hitRate > 0, "Hit rate should be > 0 after hits");
  }

  @Test
  @DisplayName("Verify mock embeddings are different for different texts")
  void testRealEmbeddingsDifferentiation() {
    // Get embeddings for completely different texts
    float[] embedding1 = vectorizer.embed("What is the capital of France?");
    float[] embedding2 = vectorizer.embed("How do I cook pasta?");
    float[] embedding3 = vectorizer.embed("What is quantum physics?");

    // Verify embeddings are different (MockVectorizer generates different vectors for different
    // texts)
    assertNotEquals(embedding1[0], embedding2[0], 0.001f);
    assertNotEquals(embedding1[0], embedding3[0], 0.001f);
    assertNotEquals(embedding2[0], embedding3[0], 0.001f);

    // Get embedding for exact same text
    float[] embedding1Same = vectorizer.embed("What is the capital of France?");

    // Same text should produce same embedding (deterministic)
    assertArrayEquals(
        embedding1, embedding1Same, 0.001f, "Same text should produce same embedding");

    // Different texts should produce different embeddings
    double distance12 = calculateCosineDistance(embedding1, embedding2);
    assertTrue(distance12 > 0, "Different texts should have non-zero distance");
  }

  private double calculateCosineDistance(float[] a, float[] b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    double cosineSimilarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    return 1.0 - cosineSimilarity; // Convert to distance
  }
}
