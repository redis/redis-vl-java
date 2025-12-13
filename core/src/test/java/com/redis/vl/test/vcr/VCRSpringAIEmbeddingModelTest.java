package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Unit tests for VCRSpringAIEmbeddingModel.
 *
 * <p>These tests demonstrate standalone VCR usage with Spring AI EmbeddingModel, without requiring
 * any RedisVL vectorizers or other RedisVL components.
 */
@DisplayName("VCRSpringAIEmbeddingModel")
class VCRSpringAIEmbeddingModelTest {

  private VCRSpringAIEmbeddingModel vcrModel;
  private MockSpringAIEmbeddingModel mockDelegate;

  @BeforeEach
  void setUp() {
    mockDelegate = new MockSpringAIEmbeddingModel();
    vcrModel = new VCRSpringAIEmbeddingModel(mockDelegate);
    vcrModel.setTestId("VCRSpringAIEmbeddingModelTest.test");
  }

  @Nested
  @DisplayName("OFF Mode - Direct passthrough")
  class OffMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.OFF);
    }

    @Test
    @DisplayName("should call delegate directly when VCR is OFF")
    void shouldCallDelegateDirectly() {
      float[] embedding = vcrModel.embed("hello world");

      assertNotNull(embedding);
      assertEquals(384, embedding.length);
      assertEquals(1, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should not record when VCR is OFF")
    void shouldNotRecordWhenOff() {
      vcrModel.embed("hello");
      vcrModel.embed("world");

      assertEquals(2, mockDelegate.callCount.get());
      assertEquals(0, vcrModel.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("RECORD Mode")
  class RecordMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.RECORD);
    }

    @Test
    @DisplayName("should call delegate and record result")
    void shouldCallDelegateAndRecord() {
      float[] embedding = vcrModel.embed("test text");

      assertNotNull(embedding);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should record multiple calls with incrementing indices")
    void shouldRecordMultipleCalls() {
      vcrModel.embed("text 1");
      vcrModel.embed("text 2");
      vcrModel.embed("text 3");

      assertEquals(3, mockDelegate.callCount.get());
      assertEquals(3, vcrModel.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("PLAYBACK Mode")
  class PlaybackMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.PLAYBACK);
    }

    @Test
    @DisplayName("should return cached embedding without calling delegate")
    void shouldReturnCachedEmbedding() {
      // Pre-load cassette
      float[] cached = new float[] {0.1f, 0.2f, 0.3f};
      vcrModel.preloadCassette("vcr:embedding:VCRSpringAIEmbeddingModelTest.test:0001", cached);

      float[] embedding = vcrModel.embed("test text");

      assertNotNull(embedding);
      assertArrayEquals(cached, embedding);
      assertEquals(0, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should throw when cassette not found in strict PLAYBACK mode")
    void shouldThrowWhenCassetteNotFound() {
      assertThrows(VCRCassetteMissingException.class, () -> vcrModel.embed("uncached text"));
    }

    @Test
    @DisplayName("should track cache hits")
    void shouldTrackCacheHits() {
      vcrModel.preloadCassette(
          "vcr:embedding:VCRSpringAIEmbeddingModelTest.test:0001", new float[] {0.1f});

      vcrModel.embed("text");

      assertEquals(1, vcrModel.getCacheHits());
      assertEquals(0, vcrModel.getCacheMisses());
    }
  }

  @Nested
  @DisplayName("PLAYBACK_OR_RECORD Mode")
  class PlaybackOrRecordMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
    }

    @Test
    @DisplayName("should return cached embedding when available")
    void shouldReturnCachedWhenAvailable() {
      float[] cached = new float[] {0.5f, 0.6f, 0.7f};
      vcrModel.preloadCassette("vcr:embedding:VCRSpringAIEmbeddingModelTest.test:0001", cached);

      float[] embedding = vcrModel.embed("test");

      assertArrayEquals(cached, embedding);
      assertEquals(0, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheHits());
    }

    @Test
    @DisplayName("should call delegate and record on cache miss")
    void shouldCallDelegateAndRecordOnMiss() {
      float[] embedding = vcrModel.embed("uncached");

      assertNotNull(embedding);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
      assertEquals(1, vcrModel.getCacheMisses());
    }

    @Test
    @DisplayName("should allow subsequent cache hits after recording")
    void shouldAllowSubsequentCacheHits() {
      // First call - cache miss, records
      vcrModel.embed("text");
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheMisses());

      // Reset counter for second test
      vcrModel.resetCallCounter();

      // Second call - cache hit from recorded value
      vcrModel.embed("text");
      assertEquals(1, mockDelegate.callCount.get()); // Still 1, not 2
      assertEquals(1, vcrModel.getCacheHits());
    }
  }

  @Nested
  @DisplayName("Batch Embedding")
  class BatchEmbedding {

    @Test
    @DisplayName("should handle batch embeddings in RECORD mode")
    void shouldHandleBatchInRecordMode() {
      vcrModel.setMode(VCRMode.RECORD);

      List<String> texts = List.of("text 1", "text 2", "text 3");

      List<float[]> embeddings = vcrModel.embed(texts);

      assertNotNull(embeddings);
      assertEquals(3, embeddings.size());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached batch embeddings")
    void shouldReturnCachedBatch() {
      vcrModel.setMode(VCRMode.PLAYBACK);

      float[][] cached = new float[][] {{0.1f, 0.2f}, {0.3f, 0.4f}};
      vcrModel.preloadBatchCassette(
          "vcr:embedding:VCRSpringAIEmbeddingModelTest.test:0001", cached);

      List<String> texts = List.of("text 1", "text 2");

      List<float[]> embeddings = vcrModel.embed(texts);

      assertEquals(2, embeddings.size());
      assertArrayEquals(cached[0], embeddings.get(0));
      assertArrayEquals(cached[1], embeddings.get(1));
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("EmbeddingResponse API")
  class EmbeddingResponseApi {

    @Test
    @DisplayName("should handle embedForResponse in RECORD mode")
    void shouldHandleEmbedForResponse() {
      vcrModel.setMode(VCRMode.RECORD);

      EmbeddingResponse response = vcrModel.embedForResponse(List.of("text 1", "text 2"));

      assertNotNull(response);
      assertEquals(2, response.getResults().size());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached batch via call() method")
    void shouldReturnCachedBatchViaCall() {
      vcrModel.setMode(VCRMode.PLAYBACK);

      float[][] cached = new float[][] {{0.1f, 0.2f}, {0.3f, 0.4f}};
      vcrModel.preloadBatchCassette(
          "vcr:embedding:VCRSpringAIEmbeddingModelTest.test:0001", cached);

      EmbeddingRequest request = new EmbeddingRequest(List.of("text 1", "text 2"), null);
      EmbeddingResponse response = vcrModel.call(request);

      assertEquals(2, response.getResults().size());
      assertArrayEquals(cached[0], response.getResults().get(0).getOutput());
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("Document Embedding")
  class DocumentEmbedding {

    @Test
    @DisplayName("should embed document content")
    void shouldEmbedDocument() {
      vcrModel.setMode(VCRMode.RECORD);

      Document document = new Document("This is a test document");
      float[] embedding = vcrModel.embed(document);

      assertNotNull(embedding);
      assertEquals(384, embedding.length);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("Model Dimension")
  class ModelDimension {

    @Test
    @DisplayName("should return delegate dimension")
    void shouldReturnDelegateDimension() {
      assertEquals(384, vcrModel.dimensions());
    }
  }

  @Nested
  @DisplayName("Delegate Access")
  class DelegateAccess {

    @Test
    @DisplayName("should provide access to underlying delegate")
    void shouldProvideAccessToDelegate() {
      EmbeddingModel delegate = vcrModel.getDelegate();

      assertSame(mockDelegate, delegate);
    }
  }

  @Nested
  @DisplayName("Statistics Reset")
  class StatisticsReset {

    @Test
    @DisplayName("should reset statistics")
    void shouldResetStatistics() {
      vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
      vcrModel.embed("text"); // Cache miss

      assertEquals(1, vcrModel.getCacheMisses());

      vcrModel.resetStatistics();

      assertEquals(0, vcrModel.getCacheHits());
      assertEquals(0, vcrModel.getCacheMisses());
    }
  }

  /** Mock Spring AI EmbeddingModel for testing. */
  static class MockSpringAIEmbeddingModel implements EmbeddingModel {
    AtomicInteger callCount = new AtomicInteger(0);
    int embeddingDimensions = 384;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
      callCount.incrementAndGet();
      List<Embedding> embeddings =
          request.getInstructions().stream()
              .map(text -> new Embedding(generateVector(text), 0))
              .toList();
      return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
      callCount.incrementAndGet();
      return generateVector(text);
    }

    @Override
    public float[] embed(Document document) {
      return embed(document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
      callCount.incrementAndGet();
      return texts.stream().map(this::generateVectorWithoutCount).toList();
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
      return call(new EmbeddingRequest(texts, null));
    }

    @Override
    public int dimensions() {
      return embeddingDimensions;
    }

    private float[] generateVector(String text) {
      float[] vector = new float[embeddingDimensions];
      int hash = text.hashCode();
      for (int i = 0; i < embeddingDimensions; i++) {
        vector[i] = (float) Math.sin(hash + i) * 0.1f;
      }
      return vector;
    }

    private float[] generateVectorWithoutCount(String text) {
      float[] vector = new float[embeddingDimensions];
      int hash = text.hashCode();
      for (int i = 0; i < embeddingDimensions; i++) {
        vector[i] = (float) Math.sin(hash + i) * 0.1f;
      }
      return vector;
    }
  }
}
