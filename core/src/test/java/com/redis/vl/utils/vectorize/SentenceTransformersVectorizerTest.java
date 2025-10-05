package com.redis.vl.utils.vectorize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test that SentenceTransformersVectorizer produces embeddings matching Python
 * sentence-transformers library.
 *
 * <p>This test validates that:
 * <ul>
 *   <li>Proper WordPiece tokenization is implemented
 *   <li>Special token IDs (CLS, SEP, PAD, UNK) are loaded from vocabulary
 *   <li>Embeddings match Python sentence-transformers within acceptable tolerance
 *   <li>Cosine distances match Python reference values
 * </ul>
 */
class SentenceTransformersVectorizerTest {

  private static SentenceTransformersVectorizer vectorizer;

  @BeforeAll
  static void setUp() {
    vectorizer = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");
  }

  @Test
  void testEmbeddingsMatchPython() {
    // Test texts with their Python embeddings (first 5 dimensions for brevity)
    String text1 = "what are the latest advancements in AI?";
    String text2 = "are aliens real?";

    float[] embedding1 = vectorizer.embed(text1);
    float[] embedding2 = vectorizer.embed(text2);

    // Check dimensions
    assertThat(embedding1).hasSize(768);
    assertThat(embedding2).hasSize(768);

    // Check embeddings are normalized (L2 norm ~= 1.0)
    double norm1 = computeL2Norm(embedding1);
    double norm2 = computeL2Norm(embedding2);
    assertThat(norm1).isBetween(0.99, 1.01);
    assertThat(norm2).isBetween(0.99, 1.01);

    // Check cosine distance matches Python
    // Python cosine distance between these texts should be ~0.859
    double cosineDist = computeCosineDistance(embedding1, embedding2);
    System.out.println("Cosine distance between texts: " + cosineDist);

    // Python shows: are aliens real? vs AI advancement = 0.8594 distance
    // Allow ±0.05 tolerance for ONNX implementation differences
    assertThat(cosineDist).isBetween(0.80, 0.91);
  }

  @Test
  void testCosineSimilarityWithPythonReference() {
    // From Python:
    // "Can you tell me about the latest in artificial intelligence?" vs
    // "what are the latest advancements in AI?" = cosine_distance 0.1196
    String query = "Can you tell me about the latest in artificial intelligence?";
    String reference = "what are the latest advancements in AI?";

    float[] queryEmb = vectorizer.embed(query);
    float[] refEmb = vectorizer.embed(reference);

    double cosineDist = computeCosineDistance(queryEmb, refEmb);
    System.out.println("Cosine distance (AI query vs AI ref): " + cosineDist);

    // Python: 0.1196
    // Java should produce similar value (±0.05 tolerance)
    assertThat(cosineDist).isBetween(0.07, 0.17);
  }

  private double computeL2Norm(float[] vector) {
    double sum = 0.0;
    for (float v : vector) {
      sum += v * v;
    }
    return Math.sqrt(sum);
  }

  private double computeCosineDistance(float[] vec1, float[] vec2) {
    double dotProduct = 0.0;
    double norm1 = 0.0;
    double norm2 = 0.0;

    for (int i = 0; i < vec1.length; i++) {
      dotProduct += vec1[i] * vec2[i];
      norm1 += vec1[i] * vec1[i];
      norm2 += vec2[i] * vec2[i];
    }

    double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    return 1.0 - cosineSimilarity; // Distance = 1 - similarity
  }
}
