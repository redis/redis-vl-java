package com.redis.vl.test.vcr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

/**
 * Stores and retrieves VCR cassettes (recorded API responses) in Redis.
 *
 * <p>Cassettes are stored as Redis JSON documents with the following key format:
 *
 * <pre>vcr:{type}:{testId}:{callIndex}</pre>
 *
 * Where:
 *
 * <ul>
 *   <li>{type} - The type of cassette (e.g., "embedding", "llm", "chat")
 *   <li>{testId} - The unique test identifier (e.g., "MyTest.testMethod")
 *   <li>{callIndex} - Zero-padded call index within the test (e.g., "0001")
 * </ul>
 */
public class VCRCassetteStore {

  private static final String KEY_PREFIX = "vcr";
  private static final Gson GSON = new Gson();

  private final JedisPooled jedis;

  /**
   * Creates a new cassette store.
   *
   * @param jedis the Redis client
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "JedisPooled is intentionally shared for connection pooling")
  public VCRCassetteStore(JedisPooled jedis) {
    this.jedis = jedis;
  }

  /**
   * Formats a cassette key.
   *
   * @param type the cassette type
   * @param testId the test identifier
   * @param callIndex the call index (1-based)
   * @return the formatted key
   */
  public static String formatKey(String type, String testId, int callIndex) {
    return String.format("%s:%s:%s:%04d", KEY_PREFIX, type, testId, callIndex);
  }

  /**
   * Parses a cassette key into its components.
   *
   * @param key the key to parse
   * @return array of [prefix, type, testId, callIndex] or null if invalid
   */
  public static String[] parseKey(String key) {
    if (key == null) {
      return null;
    }
    String[] parts = key.split(":");
    if (parts.length != 4 || !KEY_PREFIX.equals(parts[0])) {
      return null;
    }
    return parts;
  }

  /**
   * Creates a cassette JSON object for an embedding.
   *
   * @param embedding the embedding vector
   * @param testId the test identifier
   * @param model the model name
   * @return the cassette JSON object
   */
  public static JsonObject createEmbeddingCassette(float[] embedding, String testId, String model) {
    Objects.requireNonNull(embedding, "embedding cannot be null");
    Objects.requireNonNull(testId, "testId cannot be null");
    Objects.requireNonNull(model, "model cannot be null");

    JsonObject cassette = new JsonObject();
    cassette.addProperty("type", "embedding");
    cassette.addProperty("testId", testId);
    cassette.addProperty("model", model);
    cassette.addProperty("timestamp", System.currentTimeMillis());

    JsonArray embeddingArray = new JsonArray();
    for (float value : embedding) {
      embeddingArray.add(value);
    }
    cassette.add("embedding", embeddingArray);

    return cassette;
  }

  /**
   * Creates a cassette JSON object for batch embeddings.
   *
   * @param embeddings the embedding vectors
   * @param testId the test identifier
   * @param model the model name
   * @return the cassette JSON object
   */
  public static JsonObject createBatchEmbeddingCassette(
      float[][] embeddings, String testId, String model) {
    Objects.requireNonNull(embeddings, "embeddings cannot be null");
    Objects.requireNonNull(testId, "testId cannot be null");
    Objects.requireNonNull(model, "model cannot be null");

    JsonObject cassette = new JsonObject();
    cassette.addProperty("type", "batch_embedding");
    cassette.addProperty("testId", testId);
    cassette.addProperty("model", model);
    cassette.addProperty("timestamp", System.currentTimeMillis());

    JsonArray embeddingsArray = new JsonArray();
    for (float[] embedding : embeddings) {
      JsonArray embeddingArray = new JsonArray();
      for (float value : embedding) {
        embeddingArray.add(value);
      }
      embeddingsArray.add(embeddingArray);
    }
    cassette.add("embeddings", embeddingsArray);

    return cassette;
  }

  /**
   * Extracts embedding from a cassette JSON object.
   *
   * @param cassette the cassette object
   * @return the embedding vector or null if not present
   */
  public static float[] extractEmbedding(JsonObject cassette) {
    if (cassette == null || !cassette.has("embedding")) {
      return null;
    }

    JsonArray embeddingArray = cassette.getAsJsonArray("embedding");
    float[] embedding = new float[embeddingArray.size()];
    for (int i = 0; i < embeddingArray.size(); i++) {
      embedding[i] = embeddingArray.get(i).getAsFloat();
    }
    return embedding;
  }

  /**
   * Extracts batch embeddings from a cassette JSON object.
   *
   * @param cassette the cassette object
   * @return the embedding vectors or null if not present
   */
  public static float[][] extractBatchEmbeddings(JsonObject cassette) {
    if (cassette == null || !cassette.has("embeddings")) {
      return null;
    }

    JsonArray embeddingsArray = cassette.getAsJsonArray("embeddings");
    float[][] embeddings = new float[embeddingsArray.size()][];

    for (int i = 0; i < embeddingsArray.size(); i++) {
      JsonArray embeddingArray = embeddingsArray.get(i).getAsJsonArray();
      embeddings[i] = new float[embeddingArray.size()];
      for (int j = 0; j < embeddingArray.size(); j++) {
        embeddings[i][j] = embeddingArray.get(j).getAsFloat();
      }
    }
    return embeddings;
  }

  /**
   * Stores a cassette in Redis.
   *
   * @param key the cassette key
   * @param cassette the cassette data
   */
  public void store(String key, JsonObject cassette) {
    if (jedis == null) {
      throw new IllegalStateException("Redis client not initialized");
    }
    jedis.jsonSet(key, Path2.ROOT_PATH, GSON.toJson(cassette));
  }

  /**
   * Retrieves a cassette from Redis.
   *
   * @param key the cassette key
   * @return the cassette data or null if not found
   */
  public JsonObject retrieve(String key) {
    if (jedis == null) {
      return null;
    }
    Object result = jedis.jsonGet(key, Path2.ROOT_PATH);
    if (result == null) {
      return null;
    }

    // Handle both array and object responses from jsonGet
    // Some Jedis versions/configurations return arrays when using ROOT_PATH
    String jsonString = result.toString();
    com.google.gson.JsonElement element =
        GSON.fromJson(jsonString, com.google.gson.JsonElement.class);

    if (element.isJsonArray()) {
      com.google.gson.JsonArray array = element.getAsJsonArray();
      if (array.isEmpty()) {
        return null;
      }
      // Return the first element if it's wrapped in an array
      return array.get(0).getAsJsonObject();
    } else if (element.isJsonObject()) {
      return element.getAsJsonObject();
    }

    return null;
  }

  /**
   * Checks if a cassette exists.
   *
   * @param key the cassette key
   * @return true if the cassette exists
   */
  public boolean exists(String key) {
    if (jedis == null) {
      return false;
    }
    return jedis.exists(key);
  }

  /**
   * Deletes a cassette.
   *
   * @param key the cassette key
   */
  public void delete(String key) {
    if (jedis != null) {
      jedis.del(key);
    }
  }
}
