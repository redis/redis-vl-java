package com.redis.vl.utils.vectorize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Mock vectorizer for testing purposes. Generates deterministic pseudo-embeddings based on text
 * content.
 */
public class MockVectorizer extends BaseVectorizer {

  // Thread-local Random to avoid creating new instances
  private static final ThreadLocal<Random> seedRandom = ThreadLocal.withInitial(Random::new);
  private static final ThreadLocal<Random> textRandom = ThreadLocal.withInitial(Random::new);

  /**
   * Creates a new MockVectorizer.
   *
   * @param modelName The name of the mock model
   * @param dimensions The dimension of the embedding vectors
   */
  public MockVectorizer(String modelName, int dimensions) {
    super(modelName, dimensions);
  }

  @Override
  protected float[] generateEmbedding(String text) {
    // Generate a deterministic embedding based on text content
    // This simulates semantic similarity: similar texts get similar vectors

    float[] embedding = new float[dimensions];

    // Use hash as seed for deterministic randomness
    long seed = hashText(text);
    Random random = seedRandom.get();
    random.setSeed(seed);

    // Generate base embedding
    for (int i = 0; i < dimensions; i++) {
      embedding[i] = (random.nextFloat() * 2) - 1; // Range [-1, 1]
    }

    // Normalize to unit vector
    normalize(embedding);

    // Add semantic similarity simulation
    // Similar texts will have similar prefixes/keywords
    addSemanticFeatures(embedding, text.toLowerCase());

    // Re-normalize after semantic features
    normalize(embedding);

    // Add variation based on exact text hash to ensure different embeddings
    // even for semantically similar text - this creates realistic distance values
    Random textRandomInstance = textRandom.get();
    textRandomInstance.setSeed(text.hashCode());
    for (int i = 0; i < Math.min(20, dimensions); i++) {
      embedding[i] +=
          (textRandomInstance.nextFloat() - 0.5f) * 0.02f; // Very small random variation
    }

    // Final normalization
    normalize(embedding);

    return embedding;
  }

  @Override
  protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
    return texts.stream().map(this::generateEmbedding).collect(Collectors.toList());
  }

  private long hashText(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      long result = 0;
      for (int i = 0; i < 8 && i < hash.length; i++) {
        result = (result << 8) | (hash[i] & 0xFF);
      }
      return result;
    } catch (NoSuchAlgorithmException e) {
      // Fallback to simple hash
      return text.hashCode();
    }
  }

  private void normalize(float[] vector) {
    float magnitude = 0;
    for (float v : vector) {
      magnitude += v * v;
    }
    magnitude = (float) Math.sqrt(magnitude);

    if (magnitude > 0) {
      for (int i = 0; i < vector.length; i++) {
        vector[i] /= magnitude;
      }
    }
  }

  private void addSemanticFeatures(float[] embedding, String text) {
    // Simulate semantic similarity by modifying embeddings based on keywords
    // Add small variations based on exact wording to ensure similar but not identical embeddings

    // Geography questions - make capital questions more similar
    if (text.contains("capital")) {
      adjustEmbedding(embedding, 0, 0.8f); // Strong signal for capital questions
      // Add very small variation based on exact wording
      if (text.contains("capital city")) {
        adjustEmbedding(embedding, 11, 0.01f); // Very small variation
      }
    } else if (text.contains("country") || text.contains("city")) {
      adjustEmbedding(embedding, 0, 0.7f); // Still similar but slightly different
    }

    // France-related - make all France variants very similar
    if (text.contains("france") || text.contains("french") || text.contains("paris")) {
      adjustEmbedding(embedding, 1, 0.9f); // Very strong signal for France
      // Add tiny variation based on exact form
      if (text.contains("france?")) {
        adjustEmbedding(embedding, 12, 0.005f); // Tiny variation
      }
    }

    // Japan-related
    if (text.contains("japan") || text.contains("japanese") || text.contains("tokyo")) {
      adjustEmbedding(embedding, 2, 0.4f);
    }

    // Germany-related
    if (text.contains("germany") || text.contains("german") || text.contains("berlin")) {
      adjustEmbedding(embedding, 3, 0.4f);
    }

    // Cooking/food related
    if (text.contains("cook")
        || text.contains("prepare")
        || text.contains("make")
        || text.contains("pasta")
        || text.contains("spaghetti")
        || text.contains("noodles")) {
      adjustEmbedding(embedding, 4, 0.35f);
    }

    // Differentiate between "cook" and "prepare"
    if (text.contains("cook")) {
      adjustEmbedding(embedding, 15, 0.15f); // Specific adjustment for "cook"
    } else if (text.contains("prepare")) {
      adjustEmbedding(embedding, 15, 0.10f); // Different adjustment for "prepare"
    }

    // Pasta specific
    if (text.contains("pasta") || text.contains("spaghetti")) {
      adjustEmbedding(embedding, 5, 0.5f);
    }

    // Noodles
    if (text.contains("noodle")) {
      adjustEmbedding(embedding, 6, 0.4f);
    }

    // Baking
    if (text.contains("bake") || text.contains("cake")) {
      adjustEmbedding(embedding, 7, 0.4f);
    }

    // Grilling
    if (text.contains("grill") || text.contains("steak")) {
      adjustEmbedding(embedding, 8, 0.4f);
    }

    // Math/numbers
    if (text.matches(".*\\d+.*")) {
      adjustEmbedding(embedding, 9, 0.3f);
    }

    // Questions - but distinguish by topic
    if (text.contains("what")) {
      adjustEmbedding(embedding, 10, 0.2f);
      // Add variation for contractions
      if (text.contains("what's")) {
        adjustEmbedding(embedding, 13, 0.03f);
      }
    } else if (text.contains("how") || text.contains("when") || text.contains("?")) {
      adjustEmbedding(embedding, 10, 0.18f);
    }

    // Weather-related - very different from geography
    if (text.contains("weather")
        || text.contains("rain")
        || text.contains("sunny")
        || text.contains("temperature")) {
      adjustEmbedding(embedding, 14, 0.8f); // Strong signal for weather
      adjustEmbedding(embedding, 0, -0.5f); // Negative correlation with geography
    }

    // Re-normalize after adjustments
    normalize(embedding);
  }

  private void adjustEmbedding(float[] embedding, int index, float value) {
    if (index < embedding.length) {
      // Make the adjustment stronger - set more directly rather than averaging
      embedding[index] = value;
    }
  }
}
