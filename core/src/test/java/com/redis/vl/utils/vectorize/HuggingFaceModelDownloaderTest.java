package com.redis.vl.utils.vectorize;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("slow")
public class HuggingFaceModelDownloaderTest {

  @TempDir Path tempDir;
  private MockWebServer mockServer;
  private HuggingFaceModelDownloader downloader;

  @BeforeEach
  public void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();
  }

  @AfterEach
  public void tearDown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  public void testDownloadModel() throws Exception {
    // Mock config.json response
    JsonObject config = new JsonObject();
    config.addProperty("model_type", "sentence-transformers");
    config.addProperty("hidden_size", 768);
    config.addProperty("max_position_embeddings", 512);

    mockServer.enqueue(
        new MockResponse()
            .setBody(config.toString())
            .setHeader("Content-Type", "application/json"));

    // Mock model.onnx response
    byte[] mockOnnxData = "mock onnx model data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    mockServer.enqueue(
        new MockResponse()
            .setBody(new String(mockOnnxData, java.nio.charset.StandardCharsets.UTF_8))
            .setHeader("Content-Type", "application/octet-stream"));

    // Mock tokenizer.json response
    JsonObject tokenizer = new JsonObject();
    tokenizer.addProperty("version", "1.0");
    mockServer.enqueue(
        new MockResponse()
            .setBody(tokenizer.toString())
            .setHeader("Content-Type", "application/json"));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl);

    Path modelPath = downloader.downloadModel("test-model/embed");

    // Verify requests were made
    RecordedRequest configRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/main/config.json", configRequest.getPath());

    RecordedRequest modelRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/main/model.onnx", modelRequest.getPath());

    RecordedRequest tokenizerRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/main/tokenizer.json", tokenizerRequest.getPath());

    // Verify files were created
    assertTrue(Files.exists(modelPath));
    assertTrue(Files.exists(modelPath.resolve("config.json")));
    assertTrue(Files.exists(modelPath.resolve("model.onnx")));
    assertTrue(Files.exists(modelPath.resolve("tokenizer.json")));

    // Verify content
    String configContent = Files.readString(modelPath.resolve("config.json"));
    assertTrue(configContent.contains("sentence-transformers"));
  }

  @Test
  public void testCachedModel() throws Exception {
    // Create cached model files
    Path modelDir = tempDir.resolve("models").resolve("test-model").resolve("embed");
    Files.createDirectories(modelDir);
    Files.writeString(modelDir.resolve("config.json"), "{\"cached\": true}");
    Files.writeString(modelDir.resolve("model.onnx"), "cached model");
    Files.writeString(modelDir.resolve("tokenizer.json"), "{\"cached\": true}");

    downloader = new HuggingFaceModelDownloader(tempDir.toString());

    Path modelPath = downloader.downloadModel("test-model/embed");

    // Verify no requests were made (model was cached)
    RecordedRequest request = mockServer.takeRequest(100, TimeUnit.MILLISECONDS);
    assertNull(request, "Should not make HTTP requests for cached model");

    // Verify cached files are returned
    assertEquals(modelDir, modelPath);
    assertTrue(Files.exists(modelPath.resolve("config.json")));
    String content = Files.readString(modelPath.resolve("config.json"));
    assertTrue(content.contains("\"cached\": true"));
  }

  @Test
  public void testModelNotFound() throws Exception {
    mockServer.enqueue(new MockResponse().setResponseCode(404));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl);

    Exception exception =
        assertThrows(IOException.class, () -> downloader.downloadModel("invalid/model"));

    assertTrue(
        exception.getMessage().contains("404") || exception.getMessage().contains("not found"));
  }

  @Test
  public void testPartialDownloadRecovery() throws Exception {
    // Create partial download
    Path modelDir = tempDir.resolve("models").resolve("test-model").resolve("embed");
    Files.createDirectories(modelDir);
    Files.writeString(modelDir.resolve("config.json"), "{\"partial\": true}");
    // Missing model.onnx and tokenizer.json

    // Mock missing file responses
    byte[] mockOnnxData = "mock onnx model data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    mockServer.enqueue(
        new MockResponse()
            .setBody(new String(mockOnnxData, java.nio.charset.StandardCharsets.UTF_8))
            .setHeader("Content-Type", "application/octet-stream"));

    JsonObject tokenizer = new JsonObject();
    tokenizer.addProperty("version", "1.0");
    mockServer.enqueue(
        new MockResponse()
            .setBody(tokenizer.toString())
            .setHeader("Content-Type", "application/json"));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl);

    Path modelPath = downloader.downloadModel("test-model/embed");

    // Should only request missing files
    RecordedRequest modelRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/main/model.onnx", modelRequest.getPath());

    RecordedRequest tokenizerRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/main/tokenizer.json", tokenizerRequest.getPath());

    // All files should now exist
    assertTrue(Files.exists(modelPath.resolve("config.json")));
    assertTrue(Files.exists(modelPath.resolve("model.onnx")));
    assertTrue(Files.exists(modelPath.resolve("tokenizer.json")));
  }

  @Test
  public void testDownloadWithProgress() throws Exception {
    // Mock responses with content length
    JsonObject config = new JsonObject();
    config.addProperty("model_type", "sentence-transformers");

    mockServer.enqueue(
        new MockResponse()
            .setBody(config.toString())
            .setHeader("Content-Type", "application/json")
            .setHeader("Content-Length", config.toString().length()));

    byte[] mockOnnxData = new byte[1024 * 1024]; // 1MB mock data
    mockServer.enqueue(
        new MockResponse()
            .setBody(new String(mockOnnxData, java.nio.charset.StandardCharsets.UTF_8))
            .setHeader("Content-Type", "application/octet-stream")
            .setHeader("Content-Length", mockOnnxData.length));

    JsonObject tokenizer = new JsonObject();
    tokenizer.addProperty("version", "1.0");
    mockServer.enqueue(
        new MockResponse()
            .setBody(tokenizer.toString())
            .setHeader("Content-Type", "application/json")
            .setHeader("Content-Length", tokenizer.toString().length()));

    String baseUrl = mockServer.url("/").toString();

    // Track progress callbacks
    int[] progressCalls = {0};
    HuggingFaceModelDownloader.ProgressListener listener =
        (file, bytesDownloaded, totalBytes) -> {
          progressCalls[0]++;
          assertTrue(bytesDownloaded <= totalBytes);
          assertTrue(totalBytes > 0);
        };

    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl, listener);

    Path modelPath = downloader.downloadModel("test-model/embed");

    // Verify progress was reported
    assertTrue(progressCalls[0] > 0, "Progress listener should be called");

    // Verify files exist
    assertTrue(Files.exists(modelPath));
  }

  @Test
  public void testInvalidModelName() {
    downloader = new HuggingFaceModelDownloader(tempDir.toString());

    // Test various invalid model names
    assertThrows(IllegalArgumentException.class, () -> downloader.downloadModel(null));
    assertThrows(IllegalArgumentException.class, () -> downloader.downloadModel(""));
    assertThrows(IllegalArgumentException.class, () -> downloader.downloadModel(" "));
  }

  @Test
  public void testCustomRevision() throws Exception {
    // Mock config.json response for custom revision
    JsonObject config = new JsonObject();
    config.addProperty("model_type", "sentence-transformers");

    mockServer.enqueue(
        new MockResponse()
            .setBody(config.toString())
            .setHeader("Content-Type", "application/json"));

    byte[] mockOnnxData = "mock onnx model data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    mockServer.enqueue(
        new MockResponse()
            .setBody(new String(mockOnnxData, java.nio.charset.StandardCharsets.UTF_8))
            .setHeader("Content-Type", "application/octet-stream"));

    JsonObject tokenizer = new JsonObject();
    mockServer.enqueue(
        new MockResponse()
            .setBody(tokenizer.toString())
            .setHeader("Content-Type", "application/json"));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl);

    Path modelPath = downloader.downloadModel("test-model/embed", "v2.0");

    // Verify requests used custom revision
    RecordedRequest configRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/v2.0/config.json", configRequest.getPath());

    RecordedRequest modelRequest = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/test-model/embed/resolve/v2.0/model.onnx", modelRequest.getPath());

    // Verify model is stored with revision in path
    assertTrue(modelPath.toString().contains("v2.0"));
  }

  @Test
  public void testNetworkTimeout() throws Exception {
    // Enqueue response with long delay
    mockServer.enqueue(new MockResponse().setBody("{}").setBodyDelay(10, TimeUnit.SECONDS));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl, 1); // 1 second timeout

    assertThrows(IOException.class, () -> downloader.downloadModel("test-model/embed"));
  }

  @Test
  public void testCleanupOnFailure() throws Exception {
    Path modelDir = tempDir.resolve("models").resolve("test-model").resolve("embed");

    // First file succeeds
    JsonObject config = new JsonObject();
    mockServer.enqueue(new MockResponse().setBody(config.toString()));

    // Second file fails
    mockServer.enqueue(new MockResponse().setResponseCode(500));

    String baseUrl = mockServer.url("/").toString();
    downloader = new HuggingFaceModelDownloader(tempDir.toString(), baseUrl);

    assertThrows(IOException.class, () -> downloader.downloadModel("test-model/embed"));

    // Verify partial download was cleaned up
    assertFalse(Files.exists(modelDir), "Failed download should be cleaned up");
  }
}
