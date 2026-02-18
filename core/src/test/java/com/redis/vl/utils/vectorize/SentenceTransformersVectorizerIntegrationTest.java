package com.redis.vl.utils.vectorize;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration test for SentenceTransformersVectorizer that reproduces the notebook failure. */
@Tag("integration")
public class SentenceTransformersVectorizerIntegrationTest {

  @Test
  public void testRedisLangcacheEmbedV3ModelFails() {
    // This test verifies that redis/langcache-embed-v3 fails because it doesn't have ONNX format.
    // The model uses SafeTensors format which is not compatible with ONNX Runtime.
    Exception exception =
        assertThrows(
            Exception.class,
            () -> {
              SentenceTransformersVectorizer vectorizer =
                  new SentenceTransformersVectorizer("redis/langcache-embed-v3");
            });

    // Verify the exception indicates the model could not be loaded
    assertNotNull(exception.getMessage());
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
