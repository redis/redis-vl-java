package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for EmbeddingsCache based on the Python notebook:
 * redis-vl-python/docs/user_guide/10_embeddings_cache.ipynb
 *
 * <p>This test follows the Test-First Development approach, implementing the operations
 * demonstrated in the notebook.
 */
public class EmbeddingsCacheIntegrationTest extends BaseIntegrationTest {

  private static final String CACHE_NAME = "test_embeddings_cache";
  private static final String MODEL_NAME = "test-model";
  private EmbeddingsCache cache;

  @BeforeEach
  void setUp() {
    // Clear all Redis data before each test
    unifiedJedis.flushAll();
    // Initialize cache with connection to test Redis container
    cache = new EmbeddingsCache(CACHE_NAME, unifiedJedis);
  }

  @Test
  @DisplayName("Basic operations: set, get, exists, drop")
  void testBasicOperations() {
    // Test data
    String text = "Hello, world!";
    float[] embedding = new float[] {0.1f, 0.2f, 0.3f, 0.4f};

    // Set operation
    cache.set(text, MODEL_NAME, embedding);

    // Get operation
    Optional<float[]> retrieved = cache.get(text, MODEL_NAME);
    assertTrue(retrieved.isPresent(), "Embedding should be present after set");
    assertArrayEquals(embedding, retrieved.get(), 0.001f);

    // Exists operation
    assertTrue(cache.exists(text, MODEL_NAME), "Should exist after set");
    assertFalse(cache.exists("non-existent", MODEL_NAME), "Non-existent key should not exist");

    // Drop operation
    cache.drop(text, MODEL_NAME);
    assertFalse(cache.exists(text, MODEL_NAME), "Should not exist after drop");
    Optional<float[]> afterDrop = cache.get(text, MODEL_NAME);
    assertFalse(afterDrop.isPresent(), "Should return empty after drop");
  }

  @Test
  @DisplayName("Batch operations: mset, mget, mexists, mdrop")
  void testBatchOperations() {
    // Test data
    Map<String, float[]> embeddings = new HashMap<>();
    embeddings.put("text1", new float[] {0.1f, 0.2f});
    embeddings.put("text2", new float[] {0.3f, 0.4f});
    embeddings.put("text3", new float[] {0.5f, 0.6f});

    // Batch set operation
    cache.mset(embeddings, MODEL_NAME);

    // Batch get operation
    List<String> texts = Arrays.asList("text1", "text2", "text3", "text4");
    Map<String, float[]> retrieved = cache.mget(texts, MODEL_NAME);

    assertEquals(3, retrieved.size(), "Should retrieve 3 existing embeddings");
    assertArrayEquals(embeddings.get("text1"), retrieved.get("text1"), 0.001f);
    assertArrayEquals(embeddings.get("text2"), retrieved.get("text2"), 0.001f);
    assertArrayEquals(embeddings.get("text3"), retrieved.get("text3"), 0.001f);
    assertNull(retrieved.get("text4"), "Non-existent key should not be in results");

    // Batch exists operation
    Map<String, Boolean> existsResults = cache.mexists(texts, MODEL_NAME);
    assertTrue(existsResults.get("text1"));
    assertTrue(existsResults.get("text2"));
    assertTrue(existsResults.get("text3"));
    assertFalse(existsResults.get("text4"));

    // Batch drop operation
    List<String> toDrop = Arrays.asList("text1", "text3");
    cache.mdrop(toDrop, MODEL_NAME);

    // Verify dropped
    assertFalse(cache.exists("text1", MODEL_NAME));
    assertTrue(cache.exists("text2", MODEL_NAME));
    assertFalse(cache.exists("text3", MODEL_NAME));
  }

  @Test
  @DisplayName("TTL (Time-To-Live) support")
  void testTTLSupport() throws InterruptedException {
    // Test data
    String text = "temporary embedding";
    float[] embedding = new float[] {0.7f, 0.8f, 0.9f};

    // Set with TTL (2 seconds)
    cache.setWithTTL(text, MODEL_NAME, embedding, 2);

    // Verify it exists immediately
    assertTrue(cache.exists(text, MODEL_NAME));

    // Wait for TTL to expire
    Thread.sleep(2500);

    // Should no longer exist
    assertFalse(cache.exists(text, MODEL_NAME), "Should expire after TTL");

    // Test updating TTL on existing key
    String persistentText = "persistent embedding";
    float[] persistentEmbedding = new float[] {1.0f, 1.1f};
    cache.set(persistentText, MODEL_NAME, persistentEmbedding);

    // Update TTL
    cache.updateTTL(persistentText, MODEL_NAME, 3);

    // Verify it still exists
    assertTrue(cache.exists(persistentText, MODEL_NAME));

    // Wait for new TTL to expire
    Thread.sleep(3500);

    // Should no longer exist
    assertFalse(cache.exists(persistentText, MODEL_NAME), "Should expire after updated TTL");
  }

  @Test
  @DisplayName("Model name isolation")
  void testModelNameIsolation() {
    // Same text, different models
    String text = "shared text";
    float[] embeddingModel1 = new float[] {1.0f, 2.0f};
    float[] embeddingModel2 = new float[] {3.0f, 4.0f};

    // Set for different models
    cache.set(text, "model1", embeddingModel1);
    cache.set(text, "model2", embeddingModel2);

    // Retrieve and verify isolation
    Optional<float[]> fromModel1 = cache.get(text, "model1");
    Optional<float[]> fromModel2 = cache.get(text, "model2");

    assertTrue(fromModel1.isPresent());
    assertTrue(fromModel2.isPresent());
    assertArrayEquals(embeddingModel1, fromModel1.get(), 0.001f);
    assertArrayEquals(embeddingModel2, fromModel2.get(), 0.001f);

    // Drop from one model shouldn't affect the other
    cache.drop(text, "model1");
    assertFalse(cache.exists(text, "model1"));
    assertTrue(cache.exists(text, "model2"));
  }

  @Test
  @DisplayName("Clear cache")
  void testClearCache() {
    // Add multiple embeddings
    cache.set("text1", MODEL_NAME, new float[] {1.0f});
    cache.set("text2", MODEL_NAME, new float[] {2.0f});
    cache.set("text3", "other-model", new float[] {3.0f});

    // Clear entire cache
    cache.clear();

    // Verify all are gone
    assertFalse(cache.exists("text1", MODEL_NAME));
    assertFalse(cache.exists("text2", MODEL_NAME));
    assertFalse(cache.exists("text3", "other-model"));
  }

  @Test
  @DisplayName("Cache statistics")
  void testCacheStatistics() {
    // Initially empty
    assertEquals(0, cache.size());

    // Add embeddings
    cache.set("text1", MODEL_NAME, new float[] {1.0f});
    cache.set("text2", MODEL_NAME, new float[] {2.0f});
    cache.set("text3", "other-model", new float[] {3.0f});

    assertEquals(3, cache.size());

    // Drop one
    cache.drop("text1", MODEL_NAME);
    assertEquals(2, cache.size());

    // Clear all
    cache.clear();
    assertEquals(0, cache.size());
  }

  @Test
  @DisplayName("Handle special characters in text")
  void testSpecialCharacters() {
    // Test with various special characters
    String[] specialTexts = {
      "Hello, world!",
      "Text with \"quotes\"",
      "Text with 'single quotes'",
      "Text\nwith\nnewlines",
      "Text\twith\ttabs",
      "Unicode: 你好世界 🌍",
      "Symbols: @#$%^&*()",
      "Path: /usr/local/bin",
      "URL: https://example.com?param=value&other=123"
    };

    for (String text : specialTexts) {
      float[] embedding = new float[] {(float) text.hashCode()};

      // Set and get
      cache.set(text, MODEL_NAME, embedding);
      Optional<float[]> retrieved = cache.get(text, MODEL_NAME);

      assertTrue(retrieved.isPresent(), "Failed for text: " + text);
      assertArrayEquals(embedding, retrieved.get(), 0.001f);

      // Exists and drop
      assertTrue(cache.exists(text, MODEL_NAME));
      cache.drop(text, MODEL_NAME);
      assertFalse(cache.exists(text, MODEL_NAME));
    }
  }

  @Test
  @DisplayName("Performance benchmark simulation")
  void testPerformanceBenchmark() {
    int numEmbeddings = 1000;
    int embeddingDim = 384; // Typical dimension for sentence transformers

    // Generate test data
    Map<String, float[]> testData = new HashMap<>();
    for (int i = 0; i < numEmbeddings; i++) {
      String text = "text_" + i;
      float[] embedding = new float[embeddingDim];
      for (int j = 0; j < embeddingDim; j++) {
        embedding[j] = (float) (Math.random() * 2 - 1); // Random values between -1 and 1
      }
      testData.put(text, embedding);
    }

    // Benchmark batch set
    long startTime = System.currentTimeMillis();
    cache.mset(testData, MODEL_NAME);
    long setTime = System.currentTimeMillis() - startTime;

    // Benchmark batch get
    List<String> keys = testData.keySet().stream().toList();
    startTime = System.currentTimeMillis();
    Map<String, float[]> retrieved = cache.mget(keys, MODEL_NAME);
    long getTime = System.currentTimeMillis() - startTime;

    // Verify all were retrieved
    assertEquals(numEmbeddings, retrieved.size());

    // Performance assertions (generous thresholds for CI environments)
    assertTrue(setTime < 5000, "Batch set should complete within 5 seconds");
    assertTrue(getTime < 3000, "Batch get should complete within 3 seconds");

    // Clean up
    cache.clear();
  }
}
