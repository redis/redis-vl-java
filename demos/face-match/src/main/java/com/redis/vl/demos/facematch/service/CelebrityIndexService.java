package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.FaceMatch;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import java.util.stream.Collectors;
import redis.clients.jedis.UnifiedJedis;

/** Service for indexing and searching celebrity face embeddings using RedisVL. */
public class CelebrityIndexService {

  private static final String INDEX_NAME = "celebrity_faces";
  private static final String KEY_PREFIX = "celebrity";
  private static final int EMBEDDING_DIMENSION = 512;

  private final UnifiedJedis jedis;
  private SearchIndex index;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "UnifiedJedis is a connection pool meant to be shared")
  public CelebrityIndexService(UnifiedJedis jedis) {
    this.jedis = jedis;
  }

  /** Create the celebrity faces search index using dict-based schema. */
  public void createIndex() {
    Map<String, Object> schemaDict = new HashMap<>();

    // Index configuration
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", INDEX_NAME);
    indexConfig.put("prefix", KEY_PREFIX);
    indexConfig.put("storage_type", "hash");
    schemaDict.put("index", indexConfig);

    // Fields
    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of("name", "id", "type", "tag"),
            Map.of("name", "name", "type", "text"),
            Map.of("name", "imageUrl", "type", "text"),
            Map.of(
                "name", "embedding",
                "type", "vector",
                "attrs",
                    Map.of(
                        "dims", EMBEDDING_DIMENSION,
                        "algorithm", "hnsw",
                        "distance_metric", "l2",
                        "datatype", "float32")));
    schemaDict.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, jedis);
    index.create(true);
  }

  /** Index a single celebrity. */
  public void indexCelebrity(Celebrity celebrity) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", celebrity.getId());
    data.put("name", celebrity.getName());
    data.put("imageUrl", celebrity.getImageUrl());
    data.put("embedding", celebrity.getEmbedding());

    index.load(List.of(data), "id");
  }

  /** Index multiple celebrities in batch. */
  public void indexCelebrities(List<Celebrity> celebrities) {
    List<Map<String, Object>> dataList =
        celebrities.stream()
            .map(
                celeb -> {
                  Map<String, Object> data = new HashMap<>();
                  data.put("id", celeb.getId());
                  data.put("name", celeb.getName());
                  data.put("imageUrl", celeb.getImageUrl());
                  data.put("embedding", celeb.getEmbedding());
                  return data;
                })
            .collect(Collectors.toList());

    index.load(dataList, "id");
  }

  /**
   * Find similar faces using vector similarity search.
   *
   * @param queryEmbedding The face embedding to search for
   * @param k Number of results to return
   * @return List of face matches sorted by similarity
   */
  public List<FaceMatch> findSimilarFaces(float[] queryEmbedding, int k) {
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(queryEmbedding)
            .numResults(k)
            .returnFields("id", "name", "imageUrl") // Don't return embedding in query results
            .build();

    List<Map<String, Object>> results = index.query(query);

    List<FaceMatch> matches = new ArrayList<>();
    int rank = 1;
    for (Map<String, Object> result : results) {
      // Fetch full document with embedding
      String id = (String) result.get("id");
      Celebrity celebrity = getCelebrityById(id);
      if (celebrity != null) {
        double distance = parseDistance(result);
        matches.add(new FaceMatch(celebrity, distance, rank++));
      }
    }

    return matches;
  }

  /** Get a celebrity by ID. */
  public Celebrity getCelebrityById(String id) {
    Map<String, Object> result = index.fetch(KEY_PREFIX + ":" + id);
    if (result == null || result.isEmpty()) {
      return null;
    }
    return parseCelebrity(result);
  }

  /** Get all celebrities. */
  public List<Celebrity> getAllCelebrities() {
    // Use scan to get all keys with the prefix
    Set<String> keys = jedis.keys(KEY_PREFIX + ":*");
    List<Celebrity> celebrities = new ArrayList<>();

    for (String key : keys) {
      Map<String, Object> data = index.fetch(key);
      if (data != null && !data.isEmpty()) {
        celebrities.add(parseCelebrity(data));
      }
    }

    return celebrities;
  }

  /** Get count of indexed celebrities. */
  public long count() {
    Set<String> keys = jedis.keys(KEY_PREFIX + ":*");
    return keys.size();
  }

  /** Delete the index. */
  public void deleteIndex() {
    index.delete(true);
  }

  /** Get the search index. */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "SearchIndex is intentionally shared with callers for query operations")
  public SearchIndex getIndex() {
    return index;
  }

  private Celebrity parseCelebrity(Map<String, Object> result) {
    String id = (String) result.get("id");
    String name = (String) result.get("name");
    String imageUrl = (String) result.get("imageUrl");

    // Handle embedding conversion
    Object embeddingObj = result.get("embedding");
    float[] embedding;

    if (embeddingObj instanceof float[]) {
      embedding = (float[]) embeddingObj;
    } else if (embeddingObj instanceof byte[]) {
      // HashStorage returns embeddings as byte[]
      embedding = bytesToFloatArray((byte[]) embeddingObj);
    } else if (embeddingObj instanceof List) {
      @SuppressWarnings("unchecked")
      List<Number> embeddingList = (List<Number>) embeddingObj;
      embedding = new float[embeddingList.size()];
      for (int i = 0; i < embeddingList.size(); i++) {
        embedding[i] = embeddingList.get(i).floatValue();
      }
    } else if (embeddingObj instanceof double[]) {
      double[] doubleArray = (double[]) embeddingObj;
      embedding = new float[doubleArray.length];
      for (int i = 0; i < doubleArray.length; i++) {
        embedding[i] = (float) doubleArray[i];
      }
    } else {
      throw new IllegalArgumentException(
          "Unsupported embedding type: "
              + (embeddingObj != null ? embeddingObj.getClass() : "null"));
    }

    return new Celebrity(id, name, imageUrl, embedding);
  }

  private float[] bytesToFloatArray(byte[] bytes) {
    // Convert byte array to float array (little-endian)
    int floatCount = bytes.length / 4;
    float[] floats = new float[floatCount];

    for (int i = 0; i < floatCount; i++) {
      int intBits =
          ((bytes[i * 4] & 0xFF))
              | ((bytes[i * 4 + 1] & 0xFF) << 8)
              | ((bytes[i * 4 + 2] & 0xFF) << 16)
              | ((bytes[i * 4 + 3] & 0xFF) << 24);
      floats[i] = Float.intBitsToFloat(intBits);
    }

    return floats;
  }

  private double parseDistance(Map<String, Object> result) {
    Object distanceObj = result.get("vector_distance");
    if (distanceObj == null) {
      distanceObj = result.get("__embedding_score");
    }
    if (distanceObj == null) {
      return 0.0;
    }
    if (distanceObj instanceof Number) {
      return ((Number) distanceObj).doubleValue();
    }
    return Double.parseDouble(distanceObj.toString());
  }
}
