package com.redis.vl.extensions.summarization;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.math3.ml.clustering.Clusterable;

/** A sentence with its embedding, implementing Clusterable for k-means. */
public class EmbeddedSentence implements Clusterable {

  private final int index;
  private final double[] embedding;

  /**
   * Create an embedded sentence.
   *
   * @param index Original index in the sentence list (for preserving order)
   * @param embedding The BERT embedding as float array
   */
  public EmbeddedSentence(int index, float[] embedding) {
    this.index = index;
    this.embedding = toDoubleArray(embedding);
  }

  private static double[] toDoubleArray(float[] floats) {
    double[] doubles = new double[floats.length];
    for (int i = 0; i < floats.length; i++) {
      doubles[i] = floats[i];
    }
    return doubles;
  }

  /** Get the original index of this sentence. */
  public int index() {
    return index;
  }

  /** Get the embedding as double array (required by Clusterable). */
  @Override
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Clusterable interface requires direct array access for k-means performance")
  public double[] getPoint() {
    return embedding;
  }

  /** Calculate cosine similarity with another embedded sentence. */
  public double cosineSimilarity(EmbeddedSentence other) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < embedding.length; i++) {
      dotProduct += embedding[i] * other.embedding[i];
      normA += embedding[i] * embedding[i];
      normB += other.embedding[i] * other.embedding[i];
    }

    if (normA == 0 || normB == 0) return 0.0;
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
