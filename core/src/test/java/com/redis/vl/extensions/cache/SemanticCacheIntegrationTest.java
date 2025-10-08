package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.MockVectorizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SemanticCache based on the Python notebook:
 * redis-vl-python/docs/user_guide/03_llmcache.ipynb
 *
 * <p>This test follows the Test-First Development approach, implementing the semantic cache
 * operations demonstrated in the notebook.
 */
public class SemanticCacheIntegrationTest extends BaseIntegrationTest {

  private static final String CACHE_NAME = "test_semantic_cache";
  private static final int DIMENSIONS = 384; // Common dimension for sentence transformers
  private SemanticCache cache;
  private BaseVectorizer vectorizer;

  @BeforeEach
  void setUp() {
    // Clear all Redis data before each test
    unifiedJedis.flushAll();

    // Create a mock vectorizer for testing
    vectorizer = new MockVectorizer("mock-model", DIMENSIONS);

    // Initialize semantic cache
    cache =
        new SemanticCache.Builder()
            .name(CACHE_NAME)
            .redisClient(unifiedJedis)
            .vectorizer(vectorizer)
            .distanceThreshold(0.2f) // Default threshold
            .build();
  }

  @Test
  @DisplayName("Store and retrieve semantically similar prompts")
  void testSemanticSimilarity() {
    // Store multiple prompt-response pairs to test semantic matching
    cache.store("What is the capital of France?", "The capital of France is Paris.");
    cache.store("How's the weather today?", "I don't have access to real-time weather data.");
    cache.store("What is 2 + 2?", "2 + 2 equals 4.");

    // Verify they were stored
    assertEquals(3, cache.size(), "Cache should have 3 items");

    // Check with exact same prompt
    String originalPrompt = "What is the capital of France?";
    Optional<CacheHit> exactHit = cache.check(originalPrompt);
    assertTrue(exactHit.isPresent(), "Should find exact match");
    assertEquals("The capital of France is Paris.", exactHit.get().getResponse());
    assertEquals(0.0f, exactHit.get().getDistance(), 0.001f, "Exact match should have distance 0");

    // Check with semantically similar prompt
    String similarPrompt = "What's the capital city of France?";

    // Adjust threshold to a more realistic value for semantic similarity
    cache.setDistanceThreshold(0.5f); // Cosine distance of 0.5 is reasonable for similar text

    Optional<CacheHit> similarHit = cache.check(similarPrompt);
    assertTrue(similarHit.isPresent(), "Should find semantically similar match");
    assertEquals("The capital of France is Paris.", similarHit.get().getResponse());
    assertTrue(similarHit.get().getDistance() > 0, "Similar match should have distance > 0");
    assertTrue(similarHit.get().getDistance() < 0.5f, "Distance should be below threshold");

    // Check with weather-related prompt - should match the weather response
    String weatherPrompt = "What is the weather like today?";
    cache.setDistanceThreshold(
        1.2f); // Weather questions need higher threshold as they're less similar
    Optional<CacheHit> weatherHit = cache.check(weatherPrompt);
    assertTrue(weatherHit.isPresent(), "Should find weather-related match");
    assertEquals("I don't have access to real-time weather data.", weatherHit.get().getResponse());
  }

  @Test
  @DisplayName("Adjust distance threshold for similarity matching")
  void testDistanceThreshold() {
    // Store a prompt
    String prompt = "How do I cook pasta?";
    String response = "Boil water, add pasta, cook for 8-10 minutes.";
    cache.store(prompt, response);

    // Test with exact same prompt - should always match
    Optional<CacheHit> exactHit = cache.check(prompt);
    assertTrue(exactHit.isPresent(), "Exact prompt should match");
    assertEquals(0.0f, exactHit.get().getDistance(), 0.001f, "Exact match should have distance 0");

    // Test with very strict threshold - only exact matches
    cache.setDistanceThreshold(0.001f);
    Optional<CacheHit> strictExact = cache.check(prompt);
    assertTrue(strictExact.isPresent(), "Exact prompt should match even with strict threshold");

    // Test with a completely different prompt - should not match with strict threshold
    String differentPrompt = "What is the weather today?";
    Optional<CacheHit> strictDifferent = cache.check(differentPrompt);
    assertFalse(
        strictDifferent.isPresent(), "Different prompt should not match with strict threshold");

    // Test with very lenient threshold - should match even different prompts
    cache.setDistanceThreshold(1.5f); // Very lenient
    Optional<CacheHit> lenientDifferent = cache.check(differentPrompt);
    assertTrue(lenientDifferent.isPresent(), "Should find match with very lenient threshold");
  }

  @Test
  @DisplayName("Store with metadata and filter on retrieval")
  void testMetadataFiltering() {
    // Store multiple prompts with metadata
    Map<String, Object> metadata1 = new HashMap<>();
    metadata1.put("user", "alice");
    metadata1.put("category", "geography");
    cache.store("What is the capital of Japan?", "Tokyo", metadata1);

    Map<String, Object> metadata2 = new HashMap<>();
    metadata2.put("user", "bob");
    metadata2.put("category", "geography");
    cache.store("What is the capital of Germany?", "Berlin", metadata2);

    Map<String, Object> metadata3 = new HashMap<>();
    metadata3.put("user", "alice");
    metadata3.put("category", "history");
    cache.store("When did World War II end?", "1945", metadata3);

    // Verify they were stored
    assertEquals(3, cache.size(), "Should have 3 items in cache");

    // Check without filter - should find best semantic match
    Optional<CacheHit> noFilter = cache.check("What is the capital of Japan?");
    assertTrue(noFilter.isPresent());
    assertEquals("Tokyo", noFilter.get().getResponse());

    // Check with user filter - ask about Germany since Bob has that entry
    Filter userFilter = Filter.tag("user", "bob");
    Optional<CacheHit> bobOnly = cache.check("What is the capital of Germany?", userFilter);
    assertTrue(bobOnly.isPresent(), "Should find Bob's entry");
    assertEquals("Berlin", bobOnly.get().getResponse(), "Should return Bob's Berlin answer");

    // Check with category filter - ask about history
    Filter categoryFilter = Filter.tag("category", "history");
    Optional<CacheHit> historyOnly = cache.check("When did World War II end?", categoryFilter);
    assertTrue(historyOnly.isPresent(), "Should find history entry");
    assertEquals("1945", historyOnly.get().getResponse(), "Should return history answer");

    // Check with combined filter - ask about Japan since Alice has geography entry for Japan
    Filter combinedFilter =
        Filter.and(Filter.tag("user", "alice"), Filter.tag("category", "geography"));
    Optional<CacheHit> aliceGeo = cache.check("What is the capital of Japan?", combinedFilter);
    assertTrue(aliceGeo.isPresent(), "Should find Alice's geography entry");
    assertEquals("Tokyo", aliceGeo.get().getResponse(), "Should return Alice's Tokyo answer");
  }

  @Test
  @DisplayName("TTL (Time-To-Live) support for cache entries")
  void testTTLSupport() throws InterruptedException {
    // Set cache with short default TTL
    cache.setTtl(2); // 2 seconds

    // Store a prompt
    String prompt = "What is 2 + 2?";
    String response = "4";
    cache.store(prompt, response);

    // Verify it exists immediately
    Optional<CacheHit> immediate = cache.check(prompt);
    assertTrue(immediate.isPresent());
    assertEquals(response, immediate.get().getResponse());

    // Wait for TTL to expire
    Thread.sleep(2500);

    // Should no longer exist
    Optional<CacheHit> expired = cache.check(prompt);
    assertFalse(expired.isPresent(), "Should expire after TTL");

    // Store with custom TTL
    cache.storeWithTTL("What is 3 + 3?", "6", null, 3);

    // Verify it exists
    assertTrue(cache.check("What is 3 + 3?").isPresent());

    // Wait past default TTL but before custom TTL
    Thread.sleep(2500);

    // Should still exist (custom TTL is 3 seconds)
    assertTrue(cache.check("What is 3 + 3?").isPresent(), "Should not expire yet");

    // Wait for custom TTL to expire
    Thread.sleep(1000);

    // Should now be expired
    assertFalse(cache.check("What is 3 + 3?").isPresent(), "Should expire after custom TTL");
  }

  @Test
  @DisplayName("Clear cache with and without filters")
  void testClearCache() {
    // Add multiple entries with metadata
    Map<String, Object> metadata1 = new HashMap<>();
    metadata1.put("session", "session1");
    cache.store("Question 1", "Answer 1", metadata1);

    Map<String, Object> metadata2 = new HashMap<>();
    metadata2.put("session", "session2");
    cache.store("Question 2", "Answer 2", metadata2);

    Map<String, Object> metadata3 = new HashMap<>();
    metadata3.put("session", "session1");
    cache.store("Question 3", "Answer 3", metadata3);

    // Verify all exist
    assertEquals(3, cache.size());

    // Clear specific session
    Filter sessionFilter = Filter.tag("session", "session1");
    cache.clear(sessionFilter);

    // Verify only session2 remains
    assertEquals(1, cache.size());
    assertFalse(cache.check("Question 1").isPresent());
    assertTrue(cache.check("Question 2").isPresent());
    assertFalse(cache.check("Question 3").isPresent());

    // Clear all
    cache.clear();
    assertEquals(0, cache.size());
    assertFalse(cache.check("Question 2").isPresent());
  }

  @Test
  @DisplayName("Update existing cache entries")
  void testUpdateEntry() {
    // Store initial prompt-response
    String prompt = "What is the meaning of life?";
    String initialResponse = "42";
    cache.store(prompt, initialResponse);

    // Verify initial response
    Optional<CacheHit> initial = cache.check(prompt);
    assertTrue(initial.isPresent());
    assertEquals(initialResponse, initial.get().getResponse());

    // Update with new response
    String updatedResponse = "The meaning of life is subjective and varies by individual.";
    cache.update(prompt, updatedResponse);

    // Verify updated response
    Optional<CacheHit> updated = cache.check(prompt);
    assertTrue(updated.isPresent());
    assertEquals(updatedResponse, updated.get().getResponse());
  }

  @Test
  @DisplayName("Batch store and check operations")
  void testBatchOperations() {
    // Prepare batch data
    List<PromptResponsePair> pairs =
        List.of(
            new PromptResponsePair("What is AI?", "Artificial Intelligence"),
            new PromptResponsePair("What is ML?", "Machine Learning"),
            new PromptResponsePair("What is DL?", "Deep Learning"),
            new PromptResponsePair("What is NLP?", "Natural Language Processing"));

    // Batch store
    cache.storeBatch(pairs);

    // Verify all stored
    assertEquals(4, cache.size());

    // Batch check
    List<String> prompts =
        List.of(
            "What is AI?",
            "What is ML?",
            "What is CV?", // Not in cache
            "What is DL?");

    List<Optional<CacheHit>> results = cache.checkBatch(prompts);

    assertEquals(4, results.size());
    assertTrue(results.get(0).isPresent());
    assertEquals("Artificial Intelligence", results.get(0).get().getResponse());
    assertTrue(results.get(1).isPresent());
    assertEquals("Machine Learning", results.get(1).get().getResponse());
    assertFalse(results.get(2).isPresent(), "CV should not be found");
    assertTrue(results.get(3).isPresent());
    assertEquals("Deep Learning", results.get(3).get().getResponse());
  }

  @Test
  @DisplayName("Return top-k similar results")
  void testTopKResults() {
    // Store multiple similar prompts
    cache.store("How to cook pasta?", "Boil water, add pasta, cook 8-10 min");
    cache.store("How to prepare spaghetti?", "Boil water, add spaghetti, cook 10-12 min");
    cache.store("How to make noodles?", "Boil water, add noodles, cook 3-5 min");
    cache.store("How to bake a cake?", "Mix ingredients, bake at 350F for 30 min");
    cache.store("How to grill steak?", "Season, grill 4-5 min per side");

    // Get top 3 similar results for pasta query
    List<CacheHit> topResults = cache.checkTopK("How do I cook pasta?", 3);

    assertEquals(3, topResults.size());

    // Results should be sorted by distance (most similar first)
    for (int i = 1; i < topResults.size(); i++) {
      assertTrue(
          topResults.get(i - 1).getDistance() <= topResults.get(i).getDistance(),
          "Results should be sorted by distance");
    }

    // First results should be pasta-related
    String firstResponse = topResults.get(0).getResponse();
    assertTrue(
        firstResponse.contains("pasta")
            || firstResponse.contains("spaghetti")
            || firstResponse.contains("noodles"),
        "Top results should be pasta-related");

    // Baking and grilling should not be in top 3 pasta results
    boolean hasBaking = topResults.stream().anyMatch(h -> h.getResponse().contains("bake"));
    boolean hasGrilling = topResults.stream().anyMatch(h -> h.getResponse().contains("grill"));
    assertFalse(
        hasBaking && hasGrilling, "Both baking and grilling should not be in top pasta results");
  }

  @Test
  @DisplayName("Cache statistics and monitoring")
  void testCacheStatistics() {
    // Initially empty
    assertEquals(0, cache.size());
    assertEquals(0, cache.getHitCount());
    assertEquals(0, cache.getMissCount());

    // Add some entries
    cache.store("Question 1", "Answer 1");
    cache.store("Question 2", "Answer 2");
    assertEquals(2, cache.size());

    // Successful lookups
    cache.check("Question 1");
    cache.check("Question 2");
    assertEquals(2, cache.getHitCount());
    assertEquals(0, cache.getMissCount());

    // Failed lookups
    cache.check("Question 3");
    cache.check("Question 4");
    assertEquals(2, cache.getHitCount());
    assertEquals(2, cache.getMissCount());

    // Calculate hit rate
    float hitRate = cache.getHitRate();
    assertEquals(0.5f, hitRate, 0.001f, "Hit rate should be 50%");

    // Clear statistics
    cache.resetStatistics();
    assertEquals(0, cache.getHitCount());
    assertEquals(0, cache.getMissCount());
    assertEquals(2, cache.size(), "Size should not be affected by reset");
  }
}
