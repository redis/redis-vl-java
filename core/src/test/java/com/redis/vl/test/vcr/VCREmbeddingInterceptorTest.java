package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VCREmbeddingInterceptor. Tests interception logic without requiring a Redis
 * instance or actual embedding model.
 */
@DisplayName("VCREmbeddingInterceptor")
class VCREmbeddingInterceptorTest {

  private TestVCREmbeddingInterceptor interceptor;
  private MockEmbeddingProvider mockProvider;

  @BeforeEach
  void setUp() {
    mockProvider = new MockEmbeddingProvider();
    interceptor = new TestVCREmbeddingInterceptor(mockProvider);
  }

  @Nested
  @DisplayName("Recording Mode")
  class RecordingMode {

    @BeforeEach
    void setUp() {
      interceptor.setMode(VCRMode.RECORD);
      interceptor.setTestId("TestClass.testMethod");
    }

    @Test
    @DisplayName("should call real provider and record result in RECORD mode")
    void shouldCallRealProviderAndRecord() {
      float[] result = interceptor.embed("test text");

      assertNotNull(result);
      assertEquals(1, mockProvider.callCount.get());
      assertEquals(1, interceptor.getRecordedCount());
    }

    @Test
    @DisplayName("should record multiple calls with incrementing indices")
    void shouldRecordMultipleCallsWithIndices() {
      interceptor.embed("text 1");
      interceptor.embed("text 2");
      interceptor.embed("text 3");

      assertEquals(3, mockProvider.callCount.get());
      assertEquals(3, interceptor.getRecordedCount());

      // Verify keys are sequential
      List<String> keys = interceptor.getRecordedKeys();
      assertTrue(keys.get(0).endsWith(":0001"));
      assertTrue(keys.get(1).endsWith(":0002"));
      assertTrue(keys.get(2).endsWith(":0003"));
    }

    @Test
    @DisplayName("should record batch embeddings")
    void shouldRecordBatchEmbeddings() {
      List<float[]> results = interceptor.embedBatch(List.of("text 1", "text 2"));

      assertNotNull(results);
      assertEquals(2, results.size());
      assertEquals(1, interceptor.getRecordedCount()); // Batch recorded as one cassette
    }
  }

  @Nested
  @DisplayName("Playback Mode")
  class PlaybackMode {

    @BeforeEach
    void setUp() {
      interceptor.setMode(VCRMode.PLAYBACK);
      interceptor.setTestId("TestClass.testMethod");
    }

    @Test
    @DisplayName("should return cached result without calling provider")
    void shouldReturnCachedWithoutCallingProvider() {
      // Pre-load cache
      float[] expected = new float[] {0.1f, 0.2f, 0.3f};
      interceptor.preloadCassette("vcr:embedding:TestClass.testMethod:0001", expected);

      float[] result = interceptor.embed("test text");

      assertArrayEquals(expected, result);
      assertEquals(0, mockProvider.callCount.get());
    }

    @Test
    @DisplayName("should throw when cassette not found in strict PLAYBACK mode")
    void shouldThrowWhenCassetteNotFound() {
      assertThrows(
          VCRCassetteMissingException.class, () -> interceptor.embed("text with no cassette"));
    }

    @Test
    @DisplayName("should return cached batch embeddings")
    void shouldReturnCachedBatch() {
      // Pre-load batch cassette
      float[][] expected = new float[][] {{0.1f, 0.2f}, {0.3f, 0.4f}};
      interceptor.preloadBatchCassette("vcr:embedding:TestClass.testMethod:0001", expected);

      List<float[]> results = interceptor.embedBatch(List.of("text 1", "text 2"));

      assertEquals(2, results.size());
      assertArrayEquals(expected[0], results.get(0));
      assertArrayEquals(expected[1], results.get(1));
      assertEquals(0, mockProvider.callCount.get());
    }
  }

  @Nested
  @DisplayName("Playback or Record Mode")
  class PlaybackOrRecordMode {

    @BeforeEach
    void setUp() {
      interceptor.setMode(VCRMode.PLAYBACK_OR_RECORD);
      interceptor.setTestId("TestClass.testMethod");
    }

    @Test
    @DisplayName("should return cached result when available")
    void shouldReturnCachedWhenAvailable() {
      float[] cached = new float[] {0.1f, 0.2f, 0.3f};
      interceptor.preloadCassette("vcr:embedding:TestClass.testMethod:0001", cached);

      float[] result = interceptor.embed("test text");

      assertArrayEquals(cached, result);
      assertEquals(0, mockProvider.callCount.get());
    }

    @Test
    @DisplayName("should call provider and record when cache miss")
    void shouldCallProviderAndRecordOnCacheMiss() {
      float[] result = interceptor.embed("uncached text");

      assertNotNull(result);
      assertEquals(1, mockProvider.callCount.get());
      assertEquals(1, interceptor.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("Off Mode")
  class OffMode {

    @Test
    @DisplayName("should bypass VCR completely when OFF")
    void shouldBypassVCRWhenOff() {
      interceptor.setMode(VCRMode.OFF);

      float[] result = interceptor.embed("test");

      assertEquals(1, mockProvider.callCount.get());
      assertEquals(0, interceptor.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("Statistics")
  class Statistics {

    @BeforeEach
    void setUp() {
      interceptor.setMode(VCRMode.PLAYBACK_OR_RECORD);
      interceptor.setTestId("TestClass.testMethod");
    }

    @Test
    @DisplayName("should track cache hits")
    void shouldTrackCacheHits() {
      interceptor.preloadCassette("vcr:embedding:TestClass.testMethod:0001", new float[] {0.1f});
      interceptor.preloadCassette("vcr:embedding:TestClass.testMethod:0002", new float[] {0.2f});

      interceptor.embed("text 1");
      interceptor.embed("text 2");

      assertEquals(2, interceptor.getCacheHits());
      assertEquals(0, interceptor.getCacheMisses());
    }

    @Test
    @DisplayName("should track cache misses")
    void shouldTrackCacheMisses() {
      interceptor.embed("uncached 1");
      interceptor.embed("uncached 2");

      assertEquals(0, interceptor.getCacheHits());
      assertEquals(2, interceptor.getCacheMisses());
    }

    @Test
    @DisplayName("should reset statistics")
    void shouldResetStatistics() {
      interceptor.embed("text");
      interceptor.resetStatistics();

      assertEquals(0, interceptor.getCacheHits());
      assertEquals(0, interceptor.getCacheMisses());
    }
  }

  // Test helper classes

  /** Mock embedding provider for testing. */
  static class MockEmbeddingProvider {
    AtomicInteger callCount = new AtomicInteger(0);
    int dimensions = 384;

    float[] embed(String text) {
      callCount.incrementAndGet();
      // Return deterministic embedding based on text hash
      float[] embedding = new float[dimensions];
      int hash = text.hashCode();
      for (int i = 0; i < dimensions; i++) {
        embedding[i] = (float) Math.sin(hash + i) * 0.1f;
      }
      return embedding;
    }

    List<float[]> embedBatch(List<String> texts) {
      callCount.incrementAndGet();
      return texts.stream().map(this::embed).toList();
    }
  }

  /** Test implementation with in-memory cassette storage. */
  static class TestVCREmbeddingInterceptor extends VCREmbeddingInterceptor {
    private final MockEmbeddingProvider provider;

    TestVCREmbeddingInterceptor(MockEmbeddingProvider provider) {
      super();
      this.provider = provider;
    }

    @Override
    protected float[] callRealEmbedding(String text) {
      return provider.embed(text);
    }

    @Override
    protected List<float[]> callRealBatchEmbedding(List<String> texts) {
      return provider.embedBatch(texts);
    }
  }
}
