package com.redis.vl.demos.facematch.service;

import ai.onnxruntime.*;
import com.redis.vl.demos.facematch.util.ModelDownloader;
import java.nio.FloatBuffer;
import java.util.Collections;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * Face embedding service using ArcFace ResNet100 ONNX model. Generates 512-dimensional
 * L2-normalized face embeddings.
 *
 * <p>Model: arcfaceresnet100-8.onnx from OpenVINO Model Zoo Input: 1x3x112x112 (batch, channels,
 * height, width) - RGB image Output: 1x512 - face embedding vector
 */
public final class ArcFaceEmbeddingService implements AutoCloseable {

  private static final int INPUT_SIZE = 112;
  private static final int EMBEDDING_DIMENSION = 512;

  private final OrtEnvironment env;
  private final OrtSession session;

  public ArcFaceEmbeddingService() {
    try {
      env = OrtEnvironment.getEnvironment();

      byte[] modelBytes = ModelDownloader.loadModelBytes();
      session = env.createSession(modelBytes);

      System.out.println("ArcFace model loaded successfully");
      System.out.println("Input: " + session.getInputNames());
      System.out.println("Output: " + session.getOutputNames());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load ArcFace model", e);
    }
  }

  /**
   * Generate a 512-dimensional face embedding from an image.
   *
   * @param image The input face image (will be resized to 112x112)
   * @return 512-dimensional L2-normalized embedding vector
   */
  public float[] generateEmbedding(Image image) {
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
      throw new RuntimeException("Failed to generate embedding", e);
    }
  }

  /**
   * Preprocess image for ArcFace model: 1. Resize to 112x112 2. Convert to RGB 3. Normalize to [0,
   * 1] 4. Convert to CHW format (channels, height, width)
   */
  private float[][][][] preprocessImage(Image image) {
    // Resize image to 112x112
    Image resized =
        new Image(
            image.getUrl(), INPUT_SIZE, INPUT_SIZE, false, true // preserveRatio=false, smooth=true
            );

    if (resized.getWidth() == 0 || resized.getHeight() == 0) {
      // If URL-based resize fails, try pixel-by-pixel resize
      resized = resizeImage(image, INPUT_SIZE, INPUT_SIZE);
    }

    PixelReader pixelReader = resized.getPixelReader();

    float[][][][] result = new float[1][3][INPUT_SIZE][INPUT_SIZE];

    // Convert to CHW format with normalization
    for (int h = 0; h < INPUT_SIZE; h++) {
      for (int w = 0; w < INPUT_SIZE; w++) {
        Color color = pixelReader.getColor(w, h);

        // Normalize to [0, 1] and convert to CHW
        result[0][0][h][w] = (float) color.getRed(); // R channel
        result[0][1][h][w] = (float) color.getGreen(); // G channel
        result[0][2][h][w] = (float) color.getBlue(); // B channel
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
