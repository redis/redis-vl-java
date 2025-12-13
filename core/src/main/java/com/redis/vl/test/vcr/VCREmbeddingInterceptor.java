package com.redis.vl.test.vcr;

import com.google.gson.JsonObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intercepts embedding calls for VCR recording and playback.
 *
 * <p>This interceptor captures embedding API calls during test recording and replays them during
 * playback, enabling deterministic and fast tests without actual API calls.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * VCREmbeddingInterceptor interceptor = new VCREmbeddingInterceptor(cassetteStore);
 * interceptor.setMode(VCRMode.PLAYBACK_OR_RECORD);
 * interceptor.setTestId("MyTest.testMethod");
 *
 * // In your test
 * float[] embedding = interceptor.embed("text to embed");
 * }</pre>
 */
public abstract class VCREmbeddingInterceptor {

  private static final String CASSETTE_TYPE = "embedding";

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

  /** Creates a new interceptor without Redis (for unit testing). */
  protected VCREmbeddingInterceptor() {
    this.cassetteStore = null;
  }

  /**
   * Creates a new interceptor with Redis cassette storage.
   *
   * @param cassetteStore the cassette store
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "VCRCassetteStore is intentionally shared for Redis connection pooling")
  public VCREmbeddingInterceptor(VCRCassetteStore cassetteStore) {
    this.cassetteStore = cassetteStore;
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

  /**
   * Generates a single embedding, intercepting based on VCR mode.
   *
   * @param text the text to embed
   * @return the embedding vector
   */
  public float[] embed(String text) {
    if (mode == VCRMode.OFF) {
      return callRealEmbedding(text);
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
    float[] embedding = callRealEmbedding(text);

    // Record if in recording mode
    if (mode.isRecordMode() || mode == VCRMode.PLAYBACK_OR_RECORD) {
      saveCassette(cassetteKey, embedding);
    }

    return embedding;
  }

  /**
   * Generates batch embeddings, intercepting based on VCR mode.
   *
   * @param texts the texts to embed
   * @return list of embedding vectors
   */
  public List<float[]> embedBatch(List<String> texts) {
    if (mode == VCRMode.OFF) {
      return callRealBatchEmbedding(texts);
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
    List<float[]> embeddings = callRealBatchEmbedding(texts);

    // Record if in recording mode
    if (mode.isRecordMode() || mode == VCRMode.PLAYBACK_OR_RECORD) {
      saveBatchCassette(cassetteKey, embeddings.toArray(new float[0][]));
    }

    return embeddings;
  }

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

  /**
   * Gets the number of recorded cassettes.
   *
   * @return the count
   */
  public int getRecordedCount() {
    return recordedKeys.size();
  }

  /**
   * Gets the recorded cassette keys.
   *
   * @return list of keys
   */
  public List<String> getRecordedKeys() {
    return new ArrayList<>(recordedKeys);
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

  /** Resets the call counter (call at start of each test). */
  public void resetCallCounter() {
    callCounter.set(0);
  }

  /**
   * Called to perform the actual embedding call. Subclasses must implement this.
   *
   * @param text the text to embed
   * @return the embedding vector
   */
  protected abstract float[] callRealEmbedding(String text);

  /**
   * Called to perform actual batch embedding call. Subclasses must implement this.
   *
   * @param texts the texts to embed
   * @return list of embedding vectors
   */
  protected abstract List<float[]> callRealBatchEmbedding(List<String> texts);

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
}
