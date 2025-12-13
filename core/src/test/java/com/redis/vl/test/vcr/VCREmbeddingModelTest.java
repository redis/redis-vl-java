package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VCREmbeddingModel.
 *
 * <p>These tests demonstrate standalone VCR usage with LangChain4J EmbeddingModel, without
 * requiring any RedisVL vectorizers or other RedisVL components.
 */
@DisplayName("VCREmbeddingModel")
class VCREmbeddingModelTest {

  private VCREmbeddingModel vcrModel;
  private MockLangChain4JEmbeddingModel mockDelegate;

  @BeforeEach
  void setUp() {
    mockDelegate = new MockLangChain4JEmbeddingModel();
    vcrModel = new VCREmbeddingModel(mockDelegate);
    vcrModel.setTestId("VCREmbeddingModelTest.test");
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
      Response<Embedding> response = vcrModel.embed("hello world");

      assertNotNull(response);
      assertNotNull(response.content());
      assertEquals(384, response.content().vector().length);
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
      Response<Embedding> response = vcrModel.embed("test text");

      assertNotNull(response);
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
      vcrModel.preloadCassette("vcr:embedding:VCREmbeddingModelTest.test:0001", cached);

      Response<Embedding> response = vcrModel.embed("test text");

      assertNotNull(response);
      assertArrayEquals(cached, response.content().vector());
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
      vcrModel.preloadCassette("vcr:embedding:VCREmbeddingModelTest.test:0001", new float[] {0.1f});

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
      vcrModel.preloadCassette("vcr:embedding:VCREmbeddingModelTest.test:0001", cached);

      Response<Embedding> response = vcrModel.embed("test");

      assertArrayEquals(cached, response.content().vector());
      assertEquals(0, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheHits());
    }

    @Test
    @DisplayName("should call delegate and record on cache miss")
    void shouldCallDelegateAndRecordOnMiss() {
      Response<Embedding> response = vcrModel.embed("uncached");

      assertNotNull(response);
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

      List<TextSegment> segments =
          List.of(
              TextSegment.from("text 1"), TextSegment.from("text 2"), TextSegment.from("text 3"));

      Response<List<Embedding>> response = vcrModel.embedAll(segments);

      assertNotNull(response);
      assertEquals(3, response.content().size());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached batch embeddings")
    void shouldReturnCachedBatch() {
      vcrModel.setMode(VCRMode.PLAYBACK);

      float[][] cached = new float[][] {{0.1f, 0.2f}, {0.3f, 0.4f}};
      vcrModel.preloadBatchCassette("vcr:embedding:VCREmbeddingModelTest.test:0001", cached);

      List<TextSegment> segments = List.of(TextSegment.from("text 1"), TextSegment.from("text 2"));

      Response<List<Embedding>> response = vcrModel.embedAll(segments);

      assertEquals(2, response.content().size());
      assertArrayEquals(cached[0], response.content().get(0).vector());
      assertArrayEquals(cached[1], response.content().get(1).vector());
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("Model Dimension")
  class ModelDimension {

    @Test
    @DisplayName("should return delegate dimension")
    void shouldReturnDelegateDimension() {
      assertEquals(384, vcrModel.dimension());
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

  /** Mock LangChain4J EmbeddingModel for testing. */
  static class MockLangChain4JEmbeddingModel implements EmbeddingModel {
    AtomicInteger callCount = new AtomicInteger(0);
    int dimensions = 384;

    @Override
    public Response<Embedding> embed(String text) {
      callCount.incrementAndGet();
      float[] vector = generateVector(text);
      return Response.from(Embedding.from(vector));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
      return embed(textSegment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
      callCount.incrementAndGet();
      List<Embedding> embeddings =
          textSegments.stream()
              .map(segment -> Embedding.from(generateVector(segment.text())))
              .toList();
      return Response.from(embeddings);
    }

    @Override
    public int dimension() {
      return dimensions;
    }

    private float[] generateVector(String text) {
      float[] vector = new float[dimensions];
      int hash = text.hashCode();
      for (int i = 0; i < dimensions; i++) {
        vector[i] = (float) Math.sin(hash + i) * 0.1f;
      }
      return vector;
    }
  }
}
