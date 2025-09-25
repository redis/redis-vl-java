package com.redis.vl.utils.vectorize;

import static org.junit.jupiter.api.Assertions.*;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
@Disabled("Requires real ONNX model files - run manually with actual models")
public class OnnxModelLoaderTest {

  @TempDir Path tempDir;

  private OnnxModelLoader modelLoader;
  private Path modelDir;

  @BeforeEach
  public void setUp() throws IOException {
    modelDir = tempDir.resolve("test-model");
    Files.createDirectories(modelDir);
    modelLoader = new OnnxModelLoader();
  }

  @Test
  public void testLoadModel() throws Exception {
    // Create mock model files
    createMockModelFiles();

    OrtSession session = modelLoader.loadModel(modelDir);

    assertNotNull(session);
    assertEquals(768, modelLoader.getEmbeddingDimension());
    assertEquals(512, modelLoader.getMaxSequenceLength());
    assertNotNull(modelLoader.getTokenizer());
  }

  @Test
  public void testTokenize() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    String text = "Hello, world!";
    long[][] tokenIds = modelLoader.tokenize(text);

    assertNotNull(tokenIds);
    assertEquals(1, tokenIds.length); // Batch size of 1
    assertTrue(tokenIds[0].length > 0);
    assertTrue(tokenIds[0].length <= modelLoader.getMaxSequenceLength());
  }

  @Test
  public void testTokenizeBatch() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    List<String> texts = List.of("Hello, world!", "This is a test.", "Machine learning is cool.");

    long[][] tokenIds = modelLoader.tokenizeBatch(texts);

    assertNotNull(tokenIds);
    assertEquals(texts.size(), tokenIds.length);

    for (long[] ids : tokenIds) {
      assertTrue(ids.length > 0);
      assertTrue(ids.length <= modelLoader.getMaxSequenceLength());
    }
  }

  @Test
  public void testInference() throws Exception {
    createMockModelFiles();
    OrtSession session = modelLoader.loadModel(modelDir);

    // Create mock input tensor
    long[][] tokenIds = {{101, 7592, 1010, 2088, 999, 102}}; // Mock token IDs

    try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {
      OnnxTensor inputTensor = OnnxTensor.createTensor(env, tokenIds);

      float[][] embeddings = modelLoader.runInference(session, inputTensor);

      assertNotNull(embeddings);
      assertEquals(1, embeddings.length); // Batch size
      assertEquals(768, embeddings[0].length); // Embedding dimension
    }
  }

  @Test
  public void testPooling() throws Exception {
    // Test mean pooling
    float[][][] tokenEmbeddings = {{{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}, {7.0f, 8.0f, 9.0f}}};

    float[][] pooled = modelLoader.meanPooling(tokenEmbeddings);

    assertNotNull(pooled);
    assertEquals(1, pooled.length);
    assertEquals(3, pooled[0].length);

    // Check mean values
    assertEquals(4.0f, pooled[0][0], 0.001); // (1+4+7)/3
    assertEquals(5.0f, pooled[0][1], 0.001); // (2+5+8)/3
    assertEquals(6.0f, pooled[0][2], 0.001); // (3+6+9)/3
  }

  @Test
  public void testNormalization() {
    float[][] embeddings = {
      {3.0f, 4.0f, 0.0f}, // Magnitude = 5
      {1.0f, 0.0f, 0.0f} // Magnitude = 1
    };

    float[][] normalized = modelLoader.normalize(embeddings);

    assertNotNull(normalized);
    assertEquals(2, normalized.length);

    // Check first vector normalization
    assertEquals(0.6f, normalized[0][0], 0.001); // 3/5
    assertEquals(0.8f, normalized[0][1], 0.001); // 4/5
    assertEquals(0.0f, normalized[0][2], 0.001); // 0/5

    // Check second vector (already normalized)
    assertEquals(1.0f, normalized[1][0], 0.001);
    assertEquals(0.0f, normalized[1][1], 0.001);

    // Verify magnitude is 1
    double mag1 =
        Math.sqrt(
            normalized[0][0] * normalized[0][0]
                + normalized[0][1] * normalized[0][1]
                + normalized[0][2] * normalized[0][2]);
    assertEquals(1.0, mag1, 0.001);
  }

  @Test
  public void testHandleLongText() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    // Create text longer than max sequence length
    StringBuilder longText = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      longText.append("This is a very long text that exceeds the maximum sequence length. ");
    }

    long[][] tokenIds = modelLoader.tokenize(longText.toString());

    assertNotNull(tokenIds);
    assertEquals(1, tokenIds.length);
    assertEquals(modelLoader.getMaxSequenceLength(), tokenIds[0].length);
  }

  @Test
  public void testSpecialCharacters() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    List<String> specialTexts =
        List.of(
            "Hello, world! 你好世界 🌍",
            "Test with special chars: @#$%^&*()",
            "Émojis are fun! 😊 🚀 💻",
            "Mixed: العربية 中文 한국어");

    for (String text : specialTexts) {
      long[][] tokenIds = modelLoader.tokenize(text);
      assertNotNull(tokenIds, "Failed to tokenize: " + text);
      assertTrue(tokenIds[0].length > 0, "Empty tokens for: " + text);
    }
  }

  @Test
  public void testMissingModelFile() {
    // Don't create model files

    assertThrows(IOException.class, () -> modelLoader.loadModel(modelDir));
  }

  @Test
  public void testInvalidConfig() throws IOException {
    // Create invalid config
    Files.writeString(modelDir.resolve("config.json"), "invalid json");
    Files.writeString(modelDir.resolve("model.onnx"), "dummy");
    Files.writeString(modelDir.resolve("tokenizer.json"), "{}");

    assertThrows(Exception.class, () -> modelLoader.loadModel(modelDir));
  }

  @Test
  public void testGetEmbeddings() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    String text = "Test embedding generation";
    List<Float> embedding = modelLoader.getEmbedding(text);

    assertNotNull(embedding);
    assertEquals(768, embedding.size());

    // Verify normalization (magnitude should be ~1)
    double magnitude = 0;
    for (Float val : embedding) {
      magnitude += val * val;
    }
    magnitude = Math.sqrt(magnitude);
    assertEquals(1.0, magnitude, 0.1);
  }

  @Test
  public void testBatchEmbeddings() throws Exception {
    createMockModelFiles();
    modelLoader.loadModel(modelDir);

    List<String> texts = List.of("First text", "Second text", "Third text");

    List<List<Float>> embeddings = modelLoader.getEmbeddings(texts);

    assertNotNull(embeddings);
    assertEquals(texts.size(), embeddings.size());

    for (List<Float> embedding : embeddings) {
      assertEquals(768, embedding.size());
    }
  }

  private void createMockModelFiles() throws IOException {
    // Create config.json
    JsonObject config = new JsonObject();
    config.addProperty("hidden_size", 768);
    config.addProperty("max_position_embeddings", 512);
    config.addProperty("model_type", "bert");
    Files.writeString(modelDir.resolve("config.json"), config.toString());

    // Create minimal tokenizer.json
    JsonObject tokenizer = new JsonObject();
    JsonObject model = new JsonObject();
    JsonObject vocab = new JsonObject();

    // Add basic vocabulary
    vocab.addProperty("[PAD]", 0);
    vocab.addProperty("[CLS]", 101);
    vocab.addProperty("[SEP]", 102);
    vocab.addProperty("[UNK]", 100);
    vocab.addProperty("hello", 7592);
    vocab.addProperty(",", 1010);
    vocab.addProperty("world", 2088);
    vocab.addProperty("!", 999);

    model.add("vocab", vocab);
    tokenizer.add("model", model);

    // Add tokenizer config
    JsonObject truncation = new JsonObject();
    truncation.addProperty("max_length", 512);
    tokenizer.add("truncation", truncation);

    Files.writeString(modelDir.resolve("tokenizer.json"), tokenizer.toString());

    // Create dummy ONNX file (would be real model in production)
    // For testing, we'll create a minimal valid ONNX file structure
    // In real tests, this would be a mock or actual small model
    Files.writeString(modelDir.resolve("model.onnx"), "ONNX_MODEL_PLACEHOLDER");
  }
}
