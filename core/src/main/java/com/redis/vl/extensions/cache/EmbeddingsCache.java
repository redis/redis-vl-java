package com.redis.vl.extensions.cache;

import com.redis.vl.utils.ArrayUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

/**
 * Cache for storing and retrieving text embeddings.
 *
 * <p>This cache stores exact embeddings for text inputs, allowing retrieval of previously computed
 * embeddings.
 */
public class EmbeddingsCache extends BaseCache {

  private static final String KEY_SEPARATOR = ":";

  /**
   * Creates a new EmbeddingsCache instance.
   *
   * @param name The name of the cache
   * @param redisClient The Redis client connection
   * @param ttl Default time-to-live in seconds for cache entries (null for no expiration)
   */
  public EmbeddingsCache(String name, UnifiedJedis redisClient, Integer ttl) {
    super(name, redisClient, ttl);
  }

  /**
   * Creates a new EmbeddingsCache instance without TTL.
   *
   * @param name The name of the cache
   * @param redisClient The Redis client connection
   */
  public EmbeddingsCache(String name, UnifiedJedis redisClient) {
    this(name, redisClient, null);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  /**
   * Generate a unique cache key for a text and model combination. Uses SHA256 hash of the text to
   * handle special characters and long texts.
   */
  private String generateKey(String text, String modelName) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      String textHash = bytesToHex(hash);
      return makeKey(modelName + KEY_SEPARATOR + textHash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Store an embedding for a text.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   * @param embedding The embedding vector
   */
  public void set(String text, String modelName, float[] embedding) {
    String key = generateKey(text, modelName);
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] valueBytes = ArrayUtils.floatArrayToBytes(embedding);
    setWithTtl(keyBytes, valueBytes, null);
  }

  /**
   * Store an embedding with a specific TTL.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   * @param embedding The embedding vector
   * @param ttl Time-to-live in seconds
   */
  public void setWithTTL(String text, String modelName, float[] embedding, int ttl) {
    String key = generateKey(text, modelName);
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] valueBytes = ArrayUtils.floatArrayToBytes(embedding);
    setWithTtl(keyBytes, valueBytes, ttl);
  }

  /**
   * Retrieve an embedding for a text.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   * @return Optional containing the embedding if found, empty otherwise
   */
  public Optional<float[]> get(String text, String modelName) {
    String key = generateKey(text, modelName);
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] valueBytes = redisClient.get(keyBytes);

    if (valueBytes == null) {
      return Optional.empty();
    }

    return Optional.of(ArrayUtils.bytesToFloatArray(valueBytes));
  }

  /**
   * Check if an embedding exists for a text.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   * @return true if the embedding exists, false otherwise
   */
  public boolean exists(String text, String modelName) {
    String key = generateKey(text, modelName);
    return redisClient.exists(key);
  }

  /**
   * Delete an embedding for a text.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   */
  public void drop(String text, String modelName) {
    String key = generateKey(text, modelName);
    redisClient.del(key);
  }

  /**
   * Update the TTL for an existing embedding.
   *
   * @param text The input text
   * @param modelName The name of the embedding model
   * @param ttl New time-to-live in seconds
   */
  public void updateTTL(String text, String modelName, int ttl) {
    String key = generateKey(text, modelName);
    expire(key, ttl);
  }

  /**
   * Store multiple embeddings in batch.
   *
   * @param embeddings Map of text to embedding vectors
   * @param modelName The name of the embedding model
   */
  public void mset(Map<String, float[]> embeddings, String modelName) {
    if (embeddings.isEmpty()) {
      return;
    }

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      for (Map.Entry<String, float[]> entry : embeddings.entrySet()) {
        String key = generateKey(entry.getKey(), modelName);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = ArrayUtils.floatArrayToBytes(entry.getValue());

        if (ttl != null && ttl > 0) {
          pipeline.setex(keyBytes, ttl, valueBytes);
        } else {
          pipeline.set(keyBytes, valueBytes);
        }
      }

      pipeline.sync();
    }
  }

  /**
   * Retrieve multiple embeddings in batch.
   *
   * @param texts List of input texts
   * @param modelName The name of the embedding model
   * @return Map of text to embedding vectors (only includes found embeddings)
   */
  public Map<String, float[]> mget(List<String> texts, String modelName) {
    if (texts.isEmpty()) {
      return new HashMap<>();
    }

    Map<String, float[]> results = new HashMap<>();
    Map<String, Response<byte[]>> responses = new HashMap<>();

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      for (String text : texts) {
        String key = generateKey(text, modelName);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        responses.put(text, pipeline.get(keyBytes));
      }

      pipeline.sync();
    }

    for (Map.Entry<String, Response<byte[]>> entry : responses.entrySet()) {
      byte[] valueBytes = entry.getValue().get();
      if (valueBytes != null) {
        results.put(entry.getKey(), ArrayUtils.bytesToFloatArray(valueBytes));
      }
    }

    return results;
  }

  /**
   * Check existence of multiple embeddings in batch.
   *
   * @param texts List of input texts
   * @param modelName The name of the embedding model
   * @return Map of text to existence boolean
   */
  public Map<String, Boolean> mexists(List<String> texts, String modelName) {
    if (texts.isEmpty()) {
      return new HashMap<>();
    }

    Map<String, Boolean> results = new HashMap<>();
    Map<String, Response<Boolean>> responses = new HashMap<>();

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      for (String text : texts) {
        String key = generateKey(text, modelName);
        responses.put(text, pipeline.exists(key));
      }

      pipeline.sync();
    }

    for (Map.Entry<String, Response<Boolean>> entry : responses.entrySet()) {
      results.put(entry.getKey(), entry.getValue().get());
    }

    return results;
  }

  /**
   * Delete multiple embeddings in batch.
   *
   * @param texts List of input texts
   * @param modelName The name of the embedding model
   */
  public void mdrop(List<String> texts, String modelName) {
    if (texts.isEmpty()) {
      return;
    }

    String[] keys = texts.stream().map(text -> generateKey(text, modelName)).toArray(String[]::new);

    redisClient.del(keys);
  }
}
