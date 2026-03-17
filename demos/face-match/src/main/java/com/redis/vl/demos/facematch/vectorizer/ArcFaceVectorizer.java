package com.redis.vl.demos.facematch.vectorizer;

import ai.onnxruntime.*;
import com.redis.vl.demos.facematch.util.ModelDownloader;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * RedisVL Vectorizer for face embeddings using ArcFace ResNet100 ONNX model. Extends BaseVectorizer
 * to integrate with RedisVL4J vectorizer infrastructure.
 *
 * <p>Model: arcfaceresnet100-8.onnx from OpenVINO Model Zoo Input: 1x3x112x112 (batch, channels,
 * height, width) - RGB image Output: 1x512 - L2-normalized face embedding vector
 */
public class ArcFaceVectorizer extends BaseVectorizer implements AutoCloseable {

  private static final int INPUT_SIZE = 112;
  private static final int EMBEDDING_DIMENSION = 512;

  private final OrtEnvironment env;
  private final OrtSession session;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Extends BaseVectorizer - cannot be made final")
  public ArcFaceVectorizer() {
    super("arcfaceresnet100-8", EMBEDDING_DIMENSION, "FLOAT32");

    try {
      env = OrtEnvironment.getEnvironment();

      byte[] modelBytes = ModelDownloader.loadModelBytes();
      session = env.createSession(modelBytes);

      System.out.println("ArcFace vectorizer initialized successfully");
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize ArcFace vectorizer", e);
    }
  }

  /**
   * Generate embedding from JavaFX Image. This is the primary method for face embedding generation.
   */
  public float[] embedImage(Image image) {
    try {
      // Preprocess image: resize to 112x112 and convert to CHW format
      float[][][][] preprocessed = preprocessImage(image);

      // Create ONNX tensor
      long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
      FloatBuffer buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE);

      // Flatten 4D array to buffer (NCHW format)
      for (int c = 0; c < 3; c++) {
        for (int h = 0; h < INPUT_SIZE; h++) {
          for (int w = 0; w < INPUT_SIZE; w++) {
            buffer.put(preprocessed[0][c][h][w]);
          }
        }
      }
      buffer.flip();

      OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);

      // Run inference
      var results = session.run(Collections.singletonMap("data", inputTensor));

      // Extract output embedding
      OnnxTensor outputTensor = (OnnxTensor) results.get(0);
      float[][] output = (float[][]) outputTensor.getValue();
      float[] embedding = output[0]; // Shape: [512]

      // L2 normalize
      embedding = normalizeVector(embedding);

      // Clean up
      inputTensor.close();
      results.close();

      return embedding;
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate face embedding", e);
    }
  }

  /**
   * BaseVectorizer requires embedText, but this is a face vectorizer. Throws
   * UnsupportedOperationException.
   */
  @Override
  protected float[] generateEmbedding(String text) {
    throw new UnsupportedOperationException(
        "ArcFaceVectorizer is for images, not text. Use embedImage(Image) instead.");
  }

  /**
   * BaseVectorizer requires batch embedding, but not supported for face embeddings. Throws
   * UnsupportedOperationException.
   */
  @Override
  protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
    throw new UnsupportedOperationException(
        "Batch text embedding not supported for ArcFaceVectorizer. Use embedImage(Image) for individual images.");
  }

  /**
   * Preprocess image for ArcFace model: 1. Resize to 112x112 2. Convert to BGR channel order 3.
   * Normalize using (pixel - 127.5) * 0.0078125 → maps [0,255] to [-1,1] 4. Convert to CHW format
   * (channels, height, width)
   */
  private float[][][][] preprocessImage(Image image) {
    // Resize image to 112x112 using pixel-by-pixel method
    Image resized = resizeImage(image, INPUT_SIZE, INPUT_SIZE);

    PixelReader pixelReader = resized.getPixelReader();

    float[][][][] result = new float[1][3][INPUT_SIZE][INPUT_SIZE];

    // Convert to CHW format with BGR ordering and proper normalization
    for (int h = 0; h < INPUT_SIZE; h++) {
      for (int w = 0; w < INPUT_SIZE; w++) {
        Color color = pixelReader.getColor(w, h);

        // Get RGB values in [0, 1] range and convert to [0, 255]
        double r = color.getRed() * 255.0;
        double g = color.getGreen() * 255.0;
        double b = color.getBlue() * 255.0;

        // Normalize using ArcFace formula: (pixel - 127.5) * 0.0078125
        // Note: BGR channel order for this model
        result[0][0][h][w] = (float) ((b - 127.5) * 0.0078125); // B channel
        result[0][1][h][w] = (float) ((g - 127.5) * 0.0078125); // G channel
        result[0][2][h][w] = (float) ((r - 127.5) * 0.0078125); // R channel
      }
    }

    return result;
  }

  /** Resize image using nearest neighbor interpolation */
  private Image resizeImage(Image source, int targetWidth, int targetHeight) {
    PixelReader reader = source.getPixelReader();
    javafx.scene.image.WritableImage resized =
        new javafx.scene.image.WritableImage(targetWidth, targetHeight);
    javafx.scene.image.PixelWriter writer = resized.getPixelWriter();

    double xRatio = source.getWidth() / targetWidth;
    double yRatio = source.getHeight() / targetHeight;

    for (int y = 0; y < targetHeight; y++) {
      for (int x = 0; x < targetWidth; x++) {
        int srcX = (int) (x * xRatio);
        int srcY = (int) (y * yRatio);
        Color color = reader.getColor(srcX, srcY);
        writer.setColor(x, y, color);
      }
    }

    return resized;
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

  @Override
  public void close() {
    try {
      if (session != null) {
        session.close();
      }
    } catch (Exception e) {
      System.err.println("Error closing ONNX session: " + e.getMessage());
    }
  }
}
