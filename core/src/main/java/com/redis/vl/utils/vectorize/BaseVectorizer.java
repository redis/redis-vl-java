package com.redis.vl.utils.vectorize;

import com.redis.vl.extensions.cache.EmbeddingsCache;
import com.redis.vl.utils.ArrayUtils;
import java.util.*;
import java.util.function.Function;

/**
 * Abstract base class for text vectorizers. Port of redis-vl-python/redisvl/utils/vectorize/base.py
 */
public abstract class BaseVectorizer {

  /** The name of the embedding model. */
  protected final String modelName;

  /** The data type for embeddings (e.g., "float32"). */
  protected final String dtype;

  /** The dimension of the embedding vectors. */
  protected int dimensions;

  /** Optional cache for storing embeddings. */
  protected Optional<EmbeddingsCache> cache;

  /**
   * Creates a new BaseVectorizer.
   *
   * @param modelName The name of the embedding model
   * @param dimensions The dimension of the embedding vectors
   */
  protected BaseVectorizer(String modelName, int dimensions) {
    this(modelName, dimensions, "float32");
  }

  /**
   * Creates a new BaseVectorizer with specified data type.
   *
   * @param modelName The name of the embedding model
   * @param dimensions The dimension of the embedding vectors (-1 for auto-detect)
   * @param dtype The data type for embeddings (default: "float32")
   */
  protected BaseVectorizer(String modelName, int dimensions, String dtype) {
    this.modelName = modelName;
    this.dimensions = dimensions;
    this.dtype = dtype != null ? dtype : "float32";
    this.cache = Optional.empty();
  }

  /**
   * Get the embeddings cache if present.
   *
   * @return Optional containing the cache, or empty if none set
   */
  public Optional<EmbeddingsCache> getCache() {
    return cache;
  }

  /**
   * Set an embeddings cache for this vectorizer.
   *
   * @param cache The embeddings cache to use
   */
  public void setCache(EmbeddingsCache cache) {
    this.cache = Optional.ofNullable(cache);
  }

  /**
   * Get the vector data type.
   *
   * @return The data type (e.g. "float32")
   */
  public String getDataType() {
    return dtype;
  }

  /**
   * Get the model name.
   *
   * @return The model name
   */
  public String getModelName() {
    return modelName;
  }

  /**
   * Get the embedding dimensions.
   *
   * @return The number of dimensions
   */
  public int getDimensions() {
    return dimensions;
  }

  /**
   * Embed a single text string.
   *
   * @param text The text to embed
   * @return The embedding vector
   */
  public float[] embed(String text) {
    return embed(text, null, false, false);
  }

  /**
   * Embed a single text string with full options.
   *
   * @param text The text to embed
   * @param preprocess Optional preprocessing function
   * @param asBuffer Return as byte buffer (not implemented in Java version)
   * @param skipCache Skip cache lookup and storage
   * @return The embedding vector
   */
  public float[] embed(
      String text, Function<String, String> preprocess, boolean asBuffer, boolean skipCache) {
    // Apply preprocessing if provided
    String processedText = preprocess != null ? preprocess.apply(text) : text;

    // Check cache first if not skipping
    if (!skipCache && cache.isPresent()) {
      Optional<float[]> cached = cache.get().get(processedText, modelName);
      if (cached.isPresent()) {
        return cached.get();
      }
    }

    // Generate embedding
    float[] embedding = generateEmbedding(processedText);

    // Auto-detect dimensions if not set
    if (dimensions <= 0 && embedding != null) {
      dimensions = embedding.length;
    }

    // Store in cache if available and not skipping
    if (!skipCache && cache.isPresent() && embedding != null) {
      cache.get().set(processedText, modelName, embedding);
    }

    return embedding;
  }

  /**
   * Convert embedding to byte buffer if requested.
   *
   * @param embedding The embedding vector
   * @param asBuffer Whether to return as bytes
   * @return The embedding as float array or byte array
   */
  protected Object processEmbedding(float[] embedding, boolean asBuffer) {
    if (asBuffer) {
      return ArrayUtils.floatArrayToBytes(embedding);
    }
    return embedding;
  }

  /**
   * Embed multiple text strings in batch.
   *
   * @param texts The texts to embed
   * @return List of embedding vectors
   */
  public List<float[]> embedBatch(List<String> texts) {
    return embedBatch(texts, null, 10, false, false);
  }

  /**
   * Embed multiple text strings with full options.
   *
   * @param texts List of texts to embed
   * @param preprocess Optional preprocessing function
   * @param batchSize Number of texts to process per batch
   * @param asBuffer Return as byte buffers (not implemented in Java)
   * @param skipCache Skip cache lookup and storage
   * @return List of embedding vectors
   */
  public List<float[]> embedBatch(
      List<String> texts,
      Function<String, String> preprocess,
      int batchSize,
      boolean asBuffer,
      boolean skipCache) {
    if (texts.isEmpty()) {
      return new ArrayList<>();
    }

    // Apply preprocessing if provided
    List<String> processedTexts = new ArrayList<>();
    for (String text : texts) {
      processedTexts.add(preprocess != null ? preprocess.apply(text) : text);
    }

    // Get cached embeddings and identify misses
    BatchCacheResult cacheResult = getFromCacheBatch(processedTexts, skipCache);
    List<float[]> results = cacheResult.results;
    List<String> cacheMisses = cacheResult.cacheMisses;
    List<Integer> cacheMissIndices = cacheResult.cacheMissIndices;

    // Generate embeddings for cache misses
    if (!cacheMisses.isEmpty()) {
      List<float[]> newEmbeddings = generateEmbeddingsBatch(cacheMisses, batchSize);

      // Store new embeddings in cache
      storeInCacheBatch(cacheMisses, newEmbeddings, skipCache);

      // Insert new embeddings into results array
      for (int i = 0; i < cacheMissIndices.size() && i < newEmbeddings.size(); i++) {
        int idx = cacheMissIndices.get(i);
        if (idx < results.size()) {
          results.set(idx, newEmbeddings.get(i));
        }
      }
    }

    return results;
  }

  /**
   * Generate embedding for a single text (to be implemented by subclasses).
   *
   * @param text The text to embed
   * @return The embedding vector
   */
  protected abstract float[] generateEmbedding(String text);

  /**
   * Generate embeddings for multiple texts in batch (to be implemented by subclasses).
   *
   * @param texts The texts to embed
   * @param batchSize Number of texts to process per batch
   * @return List of embedding vectors
   */
  protected abstract List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize);

  /** Get cached embeddings and identify cache misses. */
  private BatchCacheResult getFromCacheBatch(List<String> texts, boolean skipCache) {
    List<float[]> results = new ArrayList<>();
    List<String> cacheMisses = new ArrayList<>();
    List<Integer> cacheMissIndices = new ArrayList<>();

    if (skipCache || !cache.isPresent()) {
      // No cache, all are misses
      for (int i = 0; i < texts.size(); i++) {
        results.add(null);
        cacheMisses.add(texts.get(i));
        cacheMissIndices.add(i);
      }
    } else {
      // Check cache for each text
      Map<String, float[]> cachedResults = cache.get().mget(texts, modelName);

      for (int i = 0; i < texts.size(); i++) {
        String text = texts.get(i);
        if (cachedResults.containsKey(text)) {
          results.add(cachedResults.get(text));
        } else {
          results.add(null);
          cacheMisses.add(text);
          cacheMissIndices.add(i);
        }
      }
    }

    return new BatchCacheResult(results, cacheMisses, cacheMissIndices);
  }

  /** Store new embeddings in cache. */
  private void storeInCacheBatch(List<String> texts, List<float[]> embeddings, boolean skipCache) {
    if (skipCache || !cache.isPresent() || texts.size() != embeddings.size()) {
      return;
    }

    Map<String, float[]> toStore = new HashMap<>();
    for (int i = 0; i < texts.size(); i++) {
      toStore.put(texts.get(i), embeddings.get(i));
    }

    cache.get().mset(toStore, modelName);
  }

  /**
   * Get the vector type identifier.
   *
   * @return The type of vectorizer
   */
  public String getType() {
    return "base";
  }

  /** Helper class to hold batch cache results. */
  protected static class BatchCacheResult {
    /** The results list with nulls for cache misses. */
    public final List<float[]> results;

    /** The texts that were not found in cache. */
    public final List<String> cacheMisses;

    /** The indices of cache misses in the results list. */
    public final List<Integer> cacheMissIndices;

    /**
     * Creates a new batch cache result.
     *
     * @param results The results list with nulls for cache misses
     * @param cacheMisses The texts that were not found in cache
     * @param cacheMissIndices The indices of cache misses in the results list
     */
    public BatchCacheResult(
        List<float[]> results, List<String> cacheMisses, List<Integer> cacheMissIndices) {
      this.results = results;
      this.cacheMisses = cacheMisses;
      this.cacheMissIndices = cacheMissIndices;
    }
  }
}
