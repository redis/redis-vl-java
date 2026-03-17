package com.redis.vl.demos.facematch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;
import javafx.scene.image.Image;

/**
 * Service for generating face embeddings from images. Currently generates synthetic embeddings
 * based on image hash. TODO: Replace with actual face detection and embedding model (e.g., ONNX
 * FaceNet).
 */
public class FaceEmbeddingService {

  private static final int EMBEDDING_DIMENSION = 512;

  /**
   * Generate a 512-dimensional face embedding from an image. Currently generates a deterministic
   * synthetic embedding based on image hash.
   *
   * @param image The input face image
   * @return 512-dimensional normalized embedding vector
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "DMI_RANDOM_USED_ONLY_ONCE",
      justification = "Seeded Random for deterministic embeddings per image")
  public float[] generateEmbedding(Image image) {
    try {
      // Generate a hash from the image dimensions and pixel data
      // This creates a deterministic but synthetic embedding
      String imageSignature =
          String.format(
              "%dx%d@%.2f", (int) image.getWidth(), (int) image.getHeight(), image.getProgress());

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(imageSignature.getBytes(StandardCharsets.UTF_8));

      // Use hash as seed for reproducible random embedding
      long seed = 0;
      for (int i = 0; i < Math.min(8, hash.length); i++) {
        seed = (seed << 8) | (hash[i] & 0xFF);
      }

      Random random = new Random(seed);
      float[] embedding = new float[EMBEDDING_DIMENSION];

      // Generate random embedding with Gaussian distribution
      for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
        embedding[i] = (float) random.nextGaussian();
      }

      // Normalize to unit vector
      return normalizeVector(embedding);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate embedding", e);
    }
  }

  /** Normalize a vector to unit length (L2 norm = 1). */
  private float[] normalizeVector(float[] vector) {
    double sumSquares = 0.0;
    for (float v : vector) {
      sumSquares += v * v;
    }
    double norm = Math.sqrt(sumSquares);

    if (norm > 0) {
      float[] normalized = new float[vector.length];
      for (int i = 0; i < vector.length; i++) {
        normalized[i] = (float) (vector[i] / norm);
      }
      return normalized;
    }
    return vector;
  }
}
