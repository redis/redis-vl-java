package com.redis.vl.utils.vectorize;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration test for SentenceTransformersVectorizer that reproduces the notebook failure. */
@Tag("integration")
public class SentenceTransformersVectorizerIntegrationTest {

  @Test
  public void testRedisLangcacheEmbedV3ModelFails() {
    // This test should fail because redis/langcache-embed-v3 doesn't have ONNX format
    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              SentenceTransformersVectorizer vectorizer =
                  new SentenceTransformersVectorizer("redis/langcache-embed-v3");
            });

    assertTrue(
        exception.getMessage().contains("Failed to initialize SentenceTransformersVectorizer"));

    // Check the root cause
    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(
        cause.getMessage().contains("model.onnx")
            || cause.getMessage().contains("Model not found"));
  }

  @Test
  public void testModelWithOnnxSupportWorks() {
    // This test should pass once we use a model with ONNX support
    // For now, it might fail if the model doesn't have standard ONNX
    SentenceTransformersVectorizer vectorizer =
        new SentenceTransformersVectorizer("sentence-transformers/all-MiniLM-L6-v2");

    assertNotNull(vectorizer);
    assertTrue(vectorizer.getDimensions() > 0);

    // Test embedding generation
    float[] embedding = vectorizer.embed("What is the capital of France?");
    assertNotNull(embedding);
    assertEquals(vectorizer.getDimensions(), embedding.length);

    // Verify the embedding has reasonable values
    boolean hasNonZero = false;
    for (float value : embedding) {
      if (value != 0.0f) {
        hasNonZero = true;
        break;
      }
    }
    assertTrue(hasNonZero, "Embedding should have non-zero values");
  }
}
