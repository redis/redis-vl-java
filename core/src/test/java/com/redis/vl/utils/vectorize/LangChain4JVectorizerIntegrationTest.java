package com.redis.vl.utils.vectorize;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.extensions.cache.EmbeddingsCache;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for LangChain4J-based vectorizers.
 *
 * <p>These tests require LangChain4J dependencies to be available. If dependencies are missing,
 * tests will be skipped with appropriate messages.
 */
public class LangChain4JVectorizerIntegrationTest extends BaseIntegrationTest {

  private EmbeddingsCache cache;

  @BeforeEach
  void setUp() {
    // Clear all Redis data before each test
    unifiedJedis.flushAll();

    // Create cache for testing
    cache = new EmbeddingsCache("test-vectorizer-cache", unifiedJedis, 3600);
  }

  @Test
  @DisplayName("Create vectorizer with local AllMiniLmL6V2 model")
  void testLocalAllMiniLmL6V2Vectorizer() {
    try {
      BaseVectorizer vectorizer =
          VectorizerBuilder.local("all-minilm-l6-v2").withCache(cache).withDimensions(384).build();

      // Test single embedding
      String testText = "Hello, this is a test sentence for embedding.";
      float[] embedding = vectorizer.embed(testText);

      assertNotNull(embedding, "Embedding should not be null");
      assertEquals(384, embedding.length, "Embedding should have 384 dimensions");
      assertEquals(384, vectorizer.getDimensions(), "Vectorizer should report 384 dimensions");
      assertEquals("all-minilm-l6-v2", vectorizer.getModelName());
      assertEquals("langchain4j", vectorizer.getType());

      // Test that embeddings are normalized (typical for sentence transformers)
      double magnitude = 0;
      for (float value : embedding) {
        magnitude += value * value;
      }
      magnitude = Math.sqrt(magnitude);
      assertTrue(
          magnitude > 0.9 && magnitude < 1.1,
          "Embedding should be approximately normalized (magnitude ~1.0), but was: " + magnitude);

      // Test batch embedding
      List<String> texts =
          List.of("First test sentence", "Second test sentence", "Third test sentence");

      List<float[]> embeddings = vectorizer.embedBatch(texts);
      assertEquals(3, embeddings.size(), "Should return 3 embeddings");

      for (int i = 0; i < embeddings.size(); i++) {
        assertNotNull(embeddings.get(i), "Embedding " + i + " should not be null");
        assertEquals(384, embeddings.get(i).length, "Each embedding should have 384 dimensions");
      }

      // Test cache functionality
      // First call should generate and cache
      float[] firstCall = vectorizer.embed(testText);
      assertNotNull(firstCall);

      // Second call should retrieve from cache (should be identical)
      float[] secondCall = vectorizer.embed(testText);
      assertNotNull(secondCall);
      assertArrayEquals(firstCall, secondCall, "Cached embedding should be identical");

      // Verify cache contains the embedding
      assertTrue(
          cache.exists(testText, vectorizer.getModelName()), "Cache should contain the embedding");

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j-embeddings")) {
        // Skip test if dependency is missing
        System.err.println("Skipping test - missing dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("Create vectorizer with custom embedding model")
  void testCustomVectorizer() {
    try {
      // Use MockVectorizer as a stand-in for testing the custom builder
      MockVectorizer mockModel = new MockVectorizer("mock-model", 384);

      // Test that custom builder rejects non-EmbeddingModel objects
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            VectorizerBuilder.custom("test", mockModel).build();
          },
          "Should reject objects that don't implement EmbeddingModel interface");

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j")) {
        System.err.println("Skipping test - missing LangChain4J dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("Test vectorizer with preprocessing")
  void testVectorizerWithPreprocessing() {
    try {
      BaseVectorizer vectorizer =
          VectorizerBuilder.local("all-minilm-l6-v2").withDimensions(384).build();

      String originalText = "  HELLO WORLD  ";
      String expectedProcessedText = "hello world";

      // Test preprocessing function
      float[] embedding1 =
          vectorizer.embed(originalText, text -> text.trim().toLowerCase(), false, false);
      float[] embedding2 = vectorizer.embed(expectedProcessedText, null, false, false);

      assertNotNull(embedding1);
      assertNotNull(embedding2);

      // Embeddings should be similar (but due to MockVectorizer being deterministic based on text
      // hash,
      // they should actually be identical for the same processed text)
      assertArrayEquals(
          embedding1,
          embedding2,
          "Embeddings should be identical after preprocessing normalization");

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j-embeddings")) {
        System.err.println("Skipping test - missing dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("Test dimension auto-detection")
  void testDimensionAutoDetection() {
    try {
      // Create vectorizer without specifying dimensions using the constructor directly
      // (the local builder sets dimensions automatically, so use direct constructor for this test)
      try {
        Class<?> modelClass =
            Class.forName(
                "dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel");
        Object embeddingModel = modelClass.getDeclaredConstructor().newInstance();

        LangChain4JVectorizer vectorizer =
            new LangChain4JVectorizer(
                "test-model", (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);

        // Initially dimensions should not be detected
        assertFalse(
            vectorizer.areDimensionsDetected(), "Dimensions should not be detected initially");
        assertEquals(-1, vectorizer.getDimensions(), "Dimensions should be -1 initially");

        // After first embedding, dimensions should be auto-detected
        float[] embedding = vectorizer.embed("Test text");
        assertNotNull(embedding);

        assertTrue(
            vectorizer.areDimensionsDetected(),
            "Dimensions should be detected after first embedding");
        assertEquals(384, vectorizer.getDimensions(), "Dimensions should be auto-detected as 384");
      } catch (Exception e) {
        throw new RuntimeException("Failed to test dimension auto-detection", e);
      }

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j-embeddings")) {
        System.err.println("Skipping test - missing dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("Test error handling for invalid inputs")
  void testErrorHandling() {
    try {
      BaseVectorizer vectorizer =
          VectorizerBuilder.local("all-minilm-l6-v2").withDimensions(384).build();

      // Test null input
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            vectorizer.embed(null);
          },
          "Should throw exception for null input");

      // Test empty input
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            vectorizer.embed("");
          },
          "Should throw exception for empty input");

      // Test invalid model name in local builder
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> {
                VectorizerBuilder.local("invalid-model-name").build();
              },
              "Should throw RuntimeException for invalid model name");
      assertTrue(
          exception.getCause() instanceof IllegalArgumentException,
          "Cause should be IllegalArgumentException");

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j-embeddings")) {
        System.err.println("Skipping test - missing dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("Test cache skip functionality")
  void testCacheSkipFunctionality() {
    try {
      BaseVectorizer vectorizer =
          VectorizerBuilder.local("all-minilm-l6-v2").withCache(cache).withDimensions(384).build();

      String testText = "Test text for cache skip";

      // First call with cache enabled
      float[] embedding1 = vectorizer.embed(testText, null, false, false);
      assertNotNull(embedding1);
      assertTrue(cache.exists(testText, vectorizer.getModelName()), "Should be cached");

      // Second call with cache skip - should still work but not use cache
      float[] embedding2 = vectorizer.embed(testText, null, false, true);
      assertNotNull(embedding2);

      // Results should be identical (deterministic model)
      assertArrayEquals(embedding1, embedding2, "Results should be identical");

    } catch (RuntimeException e) {
      if (e.getMessage().contains("langchain4j-embeddings")) {
        System.err.println("Skipping test - missing dependency: " + e.getMessage());
        return;
      }
      throw e;
    }
  }
}
