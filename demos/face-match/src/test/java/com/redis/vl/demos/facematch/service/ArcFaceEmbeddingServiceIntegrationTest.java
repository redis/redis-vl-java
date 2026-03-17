package com.redis.vl.demos.facematch.service;

import static org.junit.jupiter.api.Assertions.*;

import javafx.embed.swing.JFXPanel;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration test for ArcFace face embedding service.
 *
 * <p>NOTE: Tests currently disabled - requires test face images in
 * src/test/resources/static/images/celebs/
 */
@Disabled("Requires test face image resources - add test images to enable")
public class ArcFaceEmbeddingServiceIntegrationTest {

  @BeforeAll
  static void initJavaFX() {
    // Initialize JavaFX toolkit
    new JFXPanel();
  }

  @Test
  void testGenerateEmbedding() {
    try (ArcFaceEmbeddingService service = new ArcFaceEmbeddingService()) {
      // Load a test celebrity image
      var imageStream = getClass().getResourceAsStream("/static/images/celebs/img_1.jpg");
      assertNotNull(imageStream, "Test image should exist");

      Image image = new Image(imageStream);
      assertFalse(image.isError(), "Image should load successfully");

      // Generate embedding
      float[] embedding = service.generateEmbedding(image);

      // Verify embedding properties
      assertNotNull(embedding, "Embedding should not be null");
      assertEquals(512, embedding.length, "Embedding should be 512-dimensional");

      // Verify L2 normalization (norm should be ~1.0)
      double norm = 0.0;
      for (float v : embedding) {
        norm += v * v;
      }
      norm = Math.sqrt(norm);
      assertEquals(1.0, norm, 0.01, "Embedding should be L2 normalized");

      // Verify embedding contains reasonable values
      boolean hasNonZero = false;
      for (float v : embedding) {
        assertFalse(Float.isNaN(v), "Embedding should not contain NaN");
        assertFalse(Float.isInfinite(v), "Embedding should not contain infinity");
        if (Math.abs(v) > 0.001) {
          hasNonZero = true;
        }
      }
      assertTrue(hasNonZero, "Embedding should contain non-zero values");

      System.out.println("✓ Generated 512D embedding with L2 norm: " + norm);
    } catch (Exception e) {
      fail("Should generate embedding without errors: " + e.getMessage());
    }
  }

  @Test
  void testEmbeddingConsistency() {
    try (ArcFaceEmbeddingService service = new ArcFaceEmbeddingService()) {
      // Load same image twice
      var imageStream1 = getClass().getResourceAsStream("/static/images/celebs/img_1.jpg");
      var imageStream2 = getClass().getResourceAsStream("/static/images/celebs/img_1.jpg");

      Image image1 = new Image(imageStream1);
      Image image2 = new Image(imageStream2);

      // Generate embeddings
      float[] embedding1 = service.generateEmbedding(image1);
      float[] embedding2 = service.generateEmbedding(image2);

      // Verify embeddings are identical (deterministic)
      assertArrayEquals(
          embedding1, embedding2, 0.0001f, "Same image should produce identical embeddings");

      System.out.println("✓ Embeddings are deterministic");
    } catch (Exception e) {
      fail("Should generate consistent embeddings: " + e.getMessage());
    }
  }

  @Test
  void testDifferentImagesProduceDifferentEmbeddings() {
    try (ArcFaceEmbeddingService service = new ArcFaceEmbeddingService()) {
      // Load two different celebrity images
      var imageStream1 = getClass().getResourceAsStream("/static/images/celebs/img_1.jpg");
      var imageStream2 = getClass().getResourceAsStream("/static/images/celebs/img_100.jpg");

      Image image1 = new Image(imageStream1);
      Image image2 = new Image(imageStream2);

      // Generate embeddings
      float[] embedding1 = service.generateEmbedding(image1);
      float[] embedding2 = service.generateEmbedding(image2);

      // Calculate cosine similarity
      double dotProduct = 0.0;
      for (int i = 0; i < 512; i++) {
        dotProduct += embedding1[i] * embedding2[i];
      }

      System.out.println("✓ Cosine similarity between different faces: " + dotProduct);

      // Different faces should have similarity < 0.9 (they're different people)
      assertTrue(
          dotProduct < 0.9,
          "Different faces should produce dissimilar embeddings (similarity < 0.9)");
    } catch (Exception e) {
      fail("Should handle different images: " + e.getMessage());
    }
  }

  @Test
  void testMultipleInferences() {
    try (ArcFaceEmbeddingService service = new ArcFaceEmbeddingService()) {
      // Generate embeddings for multiple images
      for (int i = 1; i <= 5; i++) {
        String imagePath = "/static/images/celebs/img_" + i + ".jpg";
        var imageStream = getClass().getResourceAsStream(imagePath);
        if (imageStream != null) {
          Image image = new Image(imageStream);
          float[] embedding = service.generateEmbedding(image);

          assertEquals(512, embedding.length, "Each embedding should be 512D");
          System.out.println("✓ Generated embedding for image " + i);
        }
      }
    } catch (Exception e) {
      fail("Should handle multiple inferences: " + e.getMessage());
    }
  }
}
