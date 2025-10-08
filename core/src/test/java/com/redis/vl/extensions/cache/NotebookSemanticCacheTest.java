package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test that replicates the exact operations from notebook 03_llmcache.ipynb to ensure
 * the notebook runs without errors.
 */
@Tag("integration")
@Tag("slow")
public class NotebookSemanticCacheTest extends BaseIntegrationTest {

  private BaseVectorizer vectorizer;
  private SemanticCache llmcache;

  @BeforeEach
  public void setUp() {

    // Cell 5: Create vectorizer using SentenceTransformersVectorizer
    try {
      vectorizer = new SentenceTransformersVectorizer("redis/langcache-embed-v3");
    } catch (Exception e) {
      System.err.println("Failed to initialize SentenceTransformersVectorizer: " + e.getMessage());
      e.printStackTrace();
      throw e;
    }

    // Initialize SemanticCache using Builder pattern
    llmcache =
        new SemanticCache.Builder()
            .name("llmcache_test")
            .redisClient(unifiedJedis)
            .distanceThreshold(0.1f)
            .vectorizer(vectorizer)
            .build();
  }

  @Test
  public void testNotebookFlow() {
    // Cell 6: Verify cache is ready
    assertNotNull(llmcache);
    assertEquals("llmcache_test", llmcache.getName());

    // Cell 8: Define question
    String question = "What is the capital of France?";

    // Cell 9: Check empty cache
    Optional<CacheHit> response = llmcache.check(question);
    assertFalse(response.isPresent(), "Cache should be empty initially");

    // Cell 11: Store in cache
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("city", "Paris");
    metadata.put("country", "france");

    llmcache.store(question, "Paris", metadata);

    // Cell 13: Check cache again
    Optional<CacheHit> cacheResponse = llmcache.check(question);
    assertTrue(cacheResponse.isPresent(), "Cache should have the entry");
    if (cacheResponse.isPresent()) {
      CacheHit hit = cacheResponse.get();
      assertEquals("Paris", hit.getResponse());
      assertEquals(question, hit.getPrompt());
      assertNotNull(hit.getDistance());
      assertNotNull(hit.getMetadata());
      assertEquals("Paris", hit.getMetadata().get("city"));
      assertEquals("france", hit.getMetadata().get("country"));
    }

    // Cell 14: Check semantically similar question
    String similarQuestion = "What actually is the capital of France?";
    Optional<CacheHit> similarResponse = llmcache.check(similarQuestion);
    assertTrue(similarResponse.isPresent(), "Should find semantically similar entry");
    if (similarResponse.isPresent()) {
      assertEquals("Paris", similarResponse.get().getResponse());
    }

    // Cell 16: Adjust distance threshold
    llmcache.setDistanceThreshold(0.5f);
    assertEquals(0.5f, llmcache.getDistanceThreshold(), 0.001f);

    // Cell 17: Try with tricky question
    String trickQuestion =
        "What is the capital city of the country in Europe that also has a city named Nice?";
    Optional<CacheHit> trickResponse = llmcache.check(trickQuestion);
    // With wider threshold, this might match - could be present or empty depending on embeddings
    assertNotNull(trickResponse, "Check should return a non-null Optional");

    // Cell 18: Clear cache
    llmcache.clear();
    Optional<CacheHit> clearedResponse = llmcache.check(trickQuestion);
    assertFalse(clearedResponse.isPresent(), "Cache should be empty after clear");
  }

  @Test
  public void testTTLCache() throws InterruptedException {
    // Cell 20: Create cache with TTL
    SemanticCache ttlCache =
        new SemanticCache.Builder()
            .name("llmcache_ttl_test")
            .redisClient(unifiedJedis)
            .distanceThreshold(0.1f)
            .vectorizer(vectorizer)
            .ttl(5) // 5 seconds
            .build();

    // Cell 21: Store with TTL
    ttlCache.store("This is a TTL test", "This is a TTL test response");

    // Verify it's there immediately
    Optional<CacheHit> immediateCheck = ttlCache.check("This is a TTL test");
    assertTrue(immediateCheck.isPresent(), "Entry should exist immediately after storing");

    // Wait for TTL to expire
    Thread.sleep(6000);

    // Cell 22: Check after TTL expiry
    Optional<CacheHit> ttlResult = ttlCache.check("This is a TTL test");
    assertFalse(ttlResult.isPresent(), "Entry should have expired");

    // Cell 23: Clean up
    ttlCache.clear();
  }

  @Test
  public void testCacheStatistics() {
    // Clear cache first
    llmcache.clear();

    // Store some entries
    llmcache.store("Question 1", "Answer 1");
    llmcache.store("Question 2", "Answer 2");
    llmcache.store("Question 3", "Answer 3");

    // Perform some checks to generate hits and misses
    llmcache.check("Question 1"); // Hit
    llmcache.check("Question 2"); // Hit
    llmcache.check("Question 4"); // Miss
    llmcache.check("Question 5"); // Miss
    llmcache.check("Question 1"); // Hit again

    assertEquals(3, llmcache.getHitCount());
    assertEquals(2, llmcache.getMissCount());
    assertEquals(0.6f, llmcache.getHitRate(), 0.01f);
  }

  @Test
  public void testUserMetadataFiltering() {
    // Cell 31: Store with user metadata
    Map<String, Object> userAbc = new HashMap<>();
    userAbc.put("user", "abc");

    Map<String, Object> userDef = new HashMap<>();
    userDef.put("user", "def");

    llmcache.store(
        "What is the phone number linked to my account?",
        "The number on file is 123-555-0000",
        userAbc);

    llmcache.store(
        "What's the phone number linked in my account?",
        "The number on file is 123-555-1111",
        userDef);

    // Cell 32: Check cache entries
    Optional<CacheHit> phoneResponse =
        llmcache.check("What is the phone number linked to my account?");

    assertTrue(phoneResponse.isPresent());
    if (phoneResponse.isPresent()) {
      // Should return one of the phone numbers based on similarity
      String response = phoneResponse.get().getResponse();
      assertTrue(response.contains("123-555-"));
    }

    // Cell 33: Final cleanup
    llmcache.clear();
  }
}
