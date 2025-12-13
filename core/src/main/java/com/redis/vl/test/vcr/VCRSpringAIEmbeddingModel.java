package com.redis.vl.test.vcr;

import com.google.gson.JsonObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * VCR-enabled wrapper around a Spring AI EmbeddingModel.
 *
 * <p>This wrapper intercepts embedding calls and routes them through the VCR system for recording
 * and playback.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // In your test
 * EmbeddingModel realModel = new OpenAiEmbeddingModel(...);
 * VCRSpringAIEmbeddingModel vcrModel = new VCRSpringAIEmbeddingModel(realModel);
 * vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
 * vcrModel.setTestId("MyTest.testMethod");
 *
 * // Use vcrModel instead of realModel
 * float[] embedding = vcrModel.embed("text");
 * }</pre>
 */
public class VCRSpringAIEmbeddingModel implements EmbeddingModel {

  private static final String CASSETTE_TYPE = "embedding";

  private final EmbeddingModel delegate;
  private final int dimensionSize;

  private VCRMode mode = VCRMode.OFF;
  private String testId;
  private String modelName = "default";
  private final AtomicInteger callCounter = new AtomicInteger(0);

  // In-memory cassette storage for unit tests (null = use Redis)
  private VCRCassetteStore cassetteStore;
  private final Map<String, float[]> inMemoryCassettes = new ConcurrentHashMap<>();
  private final Map<String, float[][]> inMemoryBatchCassettes = new ConcurrentHashMap<>();
  private final List<String> recordedKeys = new ArrayList<>();

  // Statistics
  private final AtomicLong cacheHits = new AtomicLong(0);
  private final AtomicLong cacheMisses = new AtomicLong(0);

  /**
   * Creates a VCR-enabled embedding model wrapper.
   *
   * @param delegate the real embedding model
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "EmbeddingModel delegate is intentionally shared")
  public VCRSpringAIEmbeddingModel(EmbeddingModel delegate) {
    this.delegate = delegate;
    this.dimensionSize = detectDimensions(delegate);
  }

  /**
   * Creates a VCR-enabled embedding model wrapper with Redis storage.
   *
   * @param delegate the real embedding model
   * @param cassetteStore the cassette store
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "EmbeddingModel and VCRCassetteStore are intentionally shared")
  public VCRSpringAIEmbeddingModel(EmbeddingModel delegate, VCRCassetteStore cassetteStore) {
    this.delegate = delegate;
    this.cassetteStore = cassetteStore;
    this.dimensionSize = detectDimensions(delegate);
  }

  /**
   * Sets the VCR mode.
   *
   * @param mode the mode
   */
  public void setMode(VCRMode mode) {
    this.mode = mode;
  }

  /**
   * Gets the current VCR mode.
   *
   * @return the mode
   */
  public VCRMode getMode() {
    return mode;
  }

  /**
   * Sets the current test identifier.
   *
   * @param testId the test ID
   */
  public void setTestId(String testId) {
    this.testId = testId;
    this.callCounter.set(0);
  }

  /**
   * Sets the model name for cassette metadata.
   *
   * @param modelName the model name
   */
  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  /** Resets the call counter (call at start of each test). */
  public void resetCallCounter() {
    callCounter.set(0);
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> texts = request.getInstructions();
    List<float[]> embeddings = embedBatchInternal(texts);

    List<Embedding> results =
        IntStream.range(0, embeddings.size())
            .mapToObj(i -> new Embedding(embeddings.get(i), i))
            .toList();

    return new EmbeddingResponse(results);
  }

  @Override
  public float[] embed(String text) {
    return embedInternal(text);
  }

  @Override
  public float[] embed(Document document) {
    return embedInternal(document.getText());
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    return embedBatchInternal(texts);
  }

  @Override
  public EmbeddingResponse embedForResponse(List<String> texts) {
    return call(new EmbeddingRequest(texts, null));
  }

  @Override
  public int dimensions() {
    return dimensionSize;
  }

  /**
   * Gets the cache hit count.
   *
   * @return number of cache hits
   */
  public long getCacheHits() {
    return cacheHits.get();
  }

  /**
   * Gets the cache miss count.
   *
   * @return number of cache misses
   */
  public long getCacheMisses() {
    return cacheMisses.get();
  }

  /** Resets statistics. */
  public void resetStatistics() {
    cacheHits.set(0);
    cacheMisses.set(0);
  }

  /**
   * Gets the number of recorded cassettes.
   *
   * @return the count
   */
  public int getRecordedCount() {
    return recordedKeys.size();
  }

  /**
   * Gets the underlying delegate model.
   *
   * @return the delegate
   */
  public EmbeddingModel getDelegate() {
    return delegate;
  }

  // Preload methods for testing

  /**
   * Preloads a cassette into the in-memory cache (for testing).
   *
   * @param key the cassette key
   * @param embedding the embedding to cache
   */
  public void preloadCassette(String key, float[] embedding) {
    inMemoryCassettes.put(key, embedding);
  }

  /**
   * Preloads a batch cassette into the in-memory cache (for testing).
   *
   * @param key the cassette key
   * @param embeddings the embeddings to cache
   */
  public void preloadBatchCassette(String key, float[][] embeddings) {
    inMemoryBatchCassettes.put(key, embeddings);
  }

  // Internal methods

  private float[] embedInternal(String text) {
    if (mode == VCRMode.OFF) {
      return delegate.embed(text);
    }

    String cassetteKey = generateCassetteKey();

    // Try to load from cache in playback modes
    if (mode.isPlaybackMode()) {
      float[] cached = loadCassette(cassetteKey);
      if (cached != null) {
        cacheHits.incrementAndGet();
        return cached;
      }

      // Strict playback mode - throw if not found
      if (mode == VCRMode.PLAYBACK) {
        throw new VCRCassetteMissingException(cassetteKey, testId);
      }
    }

    // Cache miss or record mode - call real API
    cacheMisses.incrementAndGet();
    float[] embedding = delegate.embed(text);

    // Record if in recording mode
    if (mode.isRecordMode() || mode == VCRMode.PLAYBACK_OR_RECORD) {
      saveCassette(cassetteKey, embedding);
    }

    return embedding;
  }

  private List<float[]> embedBatchInternal(List<String> texts) {
    if (mode == VCRMode.OFF) {
      return delegate.embed(texts);
    }

    String cassetteKey = generateCassetteKey();

    // Try to load from cache in playback modes
    if (mode.isPlaybackMode()) {
      float[][] cached = loadBatchCassette(cassetteKey);
      if (cached != null) {
        cacheHits.incrementAndGet();
        List<float[]> result = new ArrayList<>();
        for (float[] embedding : cached) {
          result.add(embedding);
        }
        return result;
      }

      // Strict playback mode - throw if not found
      if (mode == VCRMode.PLAYBACK) {
        throw new VCRCassetteMissingException(cassetteKey, testId);
      }
    }

    // Cache miss or record mode - call real API
    cacheMisses.incrementAndGet();
    List<float[]> embeddings = delegate.embed(texts);

    // Record if in recording mode
    if (mode.isRecordMode() || mode == VCRMode.PLAYBACK_OR_RECORD) {
      saveBatchCassette(cassetteKey, embeddings.toArray(new float[0][]));
    }

    return embeddings;
  }

  private String generateCassetteKey() {
    int index = callCounter.incrementAndGet();
    return VCRCassetteStore.formatKey(CASSETTE_TYPE, testId, index);
  }

  private float[] loadCassette(String key) {
    // Check in-memory first
    float[] inMemory = inMemoryCassettes.get(key);
    if (inMemory != null) {
      return inMemory;
    }

    // Check Redis
    if (cassetteStore != null) {
      JsonObject cassette = cassetteStore.retrieve(key);
      if (cassette != null) {
        return VCRCassetteStore.extractEmbedding(cassette);
      }
    }

    return null;
  }

  private float[][] loadBatchCassette(String key) {
    // Check in-memory first
    float[][] inMemory = inMemoryBatchCassettes.get(key);
    if (inMemory != null) {
      return inMemory;
    }

    // Check Redis
    if (cassetteStore != null) {
      JsonObject cassette = cassetteStore.retrieve(key);
      if (cassette != null) {
        return VCRCassetteStore.extractBatchEmbeddings(cassette);
      }
    }

    return null;
  }

  private void saveCassette(String key, float[] embedding) {
    recordedKeys.add(key);

    // Save to in-memory
    inMemoryCassettes.put(key, embedding);

    // Save to Redis if available
    if (cassetteStore != null) {
      JsonObject cassette = VCRCassetteStore.createEmbeddingCassette(embedding, testId, modelName);
      cassetteStore.store(key, cassette);
    }
  }

  private void saveBatchCassette(String key, float[][] embeddings) {
    recordedKeys.add(key);

    // Save to in-memory
    inMemoryBatchCassettes.put(key, embeddings);

    // Save to Redis if available
    if (cassetteStore != null) {
      JsonObject cassette =
          VCRCassetteStore.createBatchEmbeddingCassette(embeddings, testId, modelName);
      cassetteStore.store(key, cassette);
    }
  }

  private int detectDimensions(EmbeddingModel model) {
    // Try to detect dimensions from the model
    try {
      return model.dimensions();
    } catch (Exception e) {
      // Some models don't implement dimensions()
      return -1;
    }
  }
}
