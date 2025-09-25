package com.redis.vl.extensions.cache;

import static com.redis.vl.extensions.ExtensionConstants.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

/**
 * Semantic cache for LLM responses using vector similarity. Port of
 * redis-vl-python/redisvl/extensions/cache/llm/semantic.py
 */
public class SemanticCache extends BaseCache {

  private final SearchIndex index;
  private final BaseVectorizer vectorizer;
  // Statistics tracking
  private final AtomicLong hitCount = new AtomicLong(0);
  private final AtomicLong missCount = new AtomicLong(0);
  private float distanceThreshold;

  private SemanticCache(Builder builder) {
    super(builder.name, builder.redisClient, builder.ttl);
    this.vectorizer = builder.vectorizer;
    this.distanceThreshold = builder.distanceThreshold;

    // Create the search index
    this.index = createIndex(builder.name, builder.vectorizer.getDimensions(), builder.redisClient);

    // Ensure index exists in Redis
    try {
      index.create(true); // overwrite if exists
    } catch (Exception e) {
      // Index might already exist, try to use it
    }
  }

  private SearchIndex createIndex(String name, int dimensions, UnifiedJedis client) {
    // Create schema for semantic cache
    Map<String, Object> schema = new HashMap<>();
    schema.put("index", Map.of("name", name, "prefix", prefix, "storage_type", "hash"));

    List<Map<String, Object>> fields = new ArrayList<>();

    // Add prompt field (text)
    fields.add(Map.of("name", PROMPT_FIELD_NAME, "type", "text"));

    // Add response field (text)
    fields.add(Map.of("name", RESPONSE_FIELD_NAME, "type", "text"));

    // Add vector field for semantic search
    fields.add(
        Map.of(
            "name",
            CACHE_VECTOR_FIELD_NAME,
            "type",
            "vector",
            "attrs",
            Map.of("dims", dimensions, "algorithm", "flat", "distance_metric", "cosine")));

    // Add metadata fields
    fields.add(Map.of("name", INSERTED_AT_FIELD_NAME, "type", "numeric"));

    fields.add(Map.of("name", UPDATED_AT_FIELD_NAME, "type", "numeric"));

    // Add generic tag fields for filtering
    for (String tagField : Arrays.asList("user", "session", "category")) {
      fields.add(Map.of("name", tagField, "type", "tag"));
    }

    schema.put("fields", fields);

    return new SearchIndex(IndexSchema.fromDict(schema), client);
  }

  /**
   * Store a prompt-response pair in the cache.
   *
   * @param prompt The prompt text
   * @param response The response text
   */
  public void store(String prompt, String response) {
    store(prompt, response, null);
  }

  /**
   * Store a prompt-response pair with metadata.
   *
   * @param prompt The prompt text
   * @param response The response text
   * @param metadata Additional metadata
   */
  public void store(String prompt, String response, Map<String, Object> metadata) {
    // Generate embedding for prompt
    float[] embedding = vectorizer.embed(prompt);

    // Create document
    Map<String, Object> doc = new HashMap<>();
    doc.put(PROMPT_FIELD_NAME, prompt);
    doc.put(RESPONSE_FIELD_NAME, response);
    doc.put(CACHE_VECTOR_FIELD_NAME, embedding);
    doc.put(INSERTED_AT_FIELD_NAME, Instant.now().getEpochSecond());
    doc.put(UPDATED_AT_FIELD_NAME, Instant.now().getEpochSecond());

    // Add metadata fields
    if (metadata != null) {
      for (Map.Entry<String, Object> entry : metadata.entrySet()) {
        doc.put(entry.getKey(), entry.getValue());
      }
    }

    // Generate a unique ID for the document
    String entryId = UUID.randomUUID().toString();
    doc.put("id", entryId);

    // Store in Redis and get the actual keys created
    List<String> keys = index.load(List.of(doc), "id");

    // Set TTL if configured - use the actual key returned by the index
    if (ttl != null && ttl > 0 && !keys.isEmpty()) {
      expire(keys.get(0), ttl);
    }
  }

  /**
   * Store with custom TTL.
   *
   * @param prompt The prompt text
   * @param response The response text
   * @param metadata Additional metadata
   * @param ttl Time-to-live in seconds
   */
  public void storeWithTTL(String prompt, String response, Map<String, Object> metadata, int ttl) {
    Integer originalTtl = this.ttl;
    this.ttl = ttl;
    store(prompt, response, metadata);
    this.ttl = originalTtl;
  }

  /**
   * Check for a semantically similar prompt in the cache.
   *
   * @param prompt The prompt to check
   * @return Optional containing the cache hit if found
   */
  public Optional<CacheHit> check(String prompt) {
    return check(prompt, null);
  }

  /**
   * Check for a semantically similar prompt with filtering.
   *
   * @param prompt The prompt to check
   * @param filter Optional filter expression
   * @return Optional containing the cache hit if found
   */
  public Optional<CacheHit> check(String prompt, Filter filter) {
    // Generate embedding for query
    float[] queryEmbedding = vectorizer.embed(prompt);

    // Create vector query
    VectorQuery.Builder queryBuilder =
        VectorQuery.builder()
            .field(CACHE_VECTOR_FIELD_NAME)
            .vector(queryEmbedding)
            .numResults(filter != null ? 100 : 1) // When filtering, search more results
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .returnDistance(true); // Request the vector_distance field

    VectorQuery query = queryBuilder.build();

    // Add filter if provided
    if (filter != null) {
      query.setFilter(filter);
    }

    // Execute search
    SearchResult searchResult = index.search(query);

    if (searchResult.getTotalResults() == 0) {
      missCount.incrementAndGet();
      return Optional.empty();
    }

    Document doc = searchResult.getDocuments().get(0);

    // Extract fields
    String matchedPrompt = doc.getString(PROMPT_FIELD_NAME);
    String response = doc.getString(RESPONSE_FIELD_NAME);
    // Get the vector_distance field from the document
    // Redis returns the actual cosine distance in the vector_distance field
    // when using vector search
    float distance = 0.0f;
    Object distanceObj = doc.get("vector_distance");
    if (distanceObj != null) {
      if (distanceObj instanceof Number) {
        distance = ((Number) distanceObj).floatValue();
      } else if (distanceObj instanceof String) {
        distance = Float.parseFloat((String) distanceObj);
      }
    } else if (doc.getScore() != null) {
      // Fallback to score if vector_distance is not available
      // Note: Score in Redis is not the same as distance
      distance = 1.0f - doc.getScore().floatValue();
    }

    // Check if the distance is within threshold
    if (distance > distanceThreshold) {
      missCount.incrementAndGet();
      return Optional.empty();
    }

    hitCount.incrementAndGet();

    // Extract metadata
    Map<String, Object> metadata = new HashMap<>();
    for (Map.Entry<String, Object> entry : doc.getProperties()) {
      if (!entry.getKey().equals(PROMPT_FIELD_NAME)
          && !entry.getKey().equals(RESPONSE_FIELD_NAME)
          && !entry.getKey().equals(CACHE_VECTOR_FIELD_NAME)) {
        metadata.put(entry.getKey(), entry.getValue());
      }
    }

    return Optional.of(new CacheHit(matchedPrompt, response, distance, metadata));
  }

  /**
   * Get top-k similar results.
   *
   * @param prompt The prompt to check
   * @param k Number of results to return
   * @return List of cache hits sorted by distance
   */
  public List<CacheHit> checkTopK(String prompt, int k) {
    // Generate embedding for query
    float[] queryEmbedding = vectorizer.embed(prompt);

    // Create vector query
    VectorQuery query =
        VectorQuery.builder()
            .field(CACHE_VECTOR_FIELD_NAME)
            .vector(queryEmbedding)
            .numResults(k)
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .returnDistance(true) // Request the vector_distance field
            .build();

    // Execute search
    SearchResult searchResult = index.search(query);

    List<CacheHit> results =
        searchResult.getDocuments().stream()
            .map(
                doc -> {
                  String matchedPrompt = doc.getString(PROMPT_FIELD_NAME);
                  String response = doc.getString(RESPONSE_FIELD_NAME);

                  // Get distance from vector_distance field
                  float distance = 0.0f;
                  Object distanceObj = doc.get("vector_distance");
                  if (distanceObj != null) {
                    if (distanceObj instanceof Number) {
                      distance = ((Number) distanceObj).floatValue();
                    } else if (distanceObj instanceof String) {
                      distance = Float.parseFloat((String) distanceObj);
                    }
                  } else if (doc.getScore() != null) {
                    distance = 1.0f - doc.getScore().floatValue();
                  }

                  Map<String, Object> metadata = new HashMap<>();
                  for (Map.Entry<String, Object> entry : doc.getProperties()) {
                    if (!entry.getKey().equals(PROMPT_FIELD_NAME)
                        && !entry.getKey().equals(RESPONSE_FIELD_NAME)
                        && !entry.getKey().equals(CACHE_VECTOR_FIELD_NAME)) {
                      metadata.put(entry.getKey(), entry.getValue());
                    }
                  }

                  return new CacheHit(matchedPrompt, response, distance, metadata);
                })
            .collect(Collectors.toList());

    // Sort by distance (ascending - closest first)
    results.sort((a, b) -> Float.compare(a.getDistance(), b.getDistance()));

    return results;
  }

  /**
   * Update an existing cache entry.
   *
   * @param prompt The prompt to update
   * @param newResponse The new response
   */
  public void update(String prompt, String newResponse) {
    // Find the existing entry first
    float[] queryEmbedding = vectorizer.embed(prompt);

    VectorQuery query =
        VectorQuery.builder()
            .field(CACHE_VECTOR_FIELD_NAME)
            .vector(queryEmbedding)
            .numResults(1)
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .returnDistance(true)
            .build();

    SearchResult searchResult = index.search(query);

    if (searchResult.getTotalResults() > 0) {
      Document doc = searchResult.getDocuments().get(0);
      String docId = doc.getId();

      // Delete the old entry
      redisClient.del(docId);

      // Store the new entry with the updated response
      Map<String, Object> metadata = new HashMap<>();
      for (Map.Entry<String, Object> entry : doc.getProperties()) {
        if (!entry.getKey().equals(PROMPT_FIELD_NAME)
            && !entry.getKey().equals(RESPONSE_FIELD_NAME)
            && !entry.getKey().equals(CACHE_VECTOR_FIELD_NAME)) {
          metadata.put(entry.getKey(), entry.getValue());
        }
      }
      store(prompt, newResponse, metadata);
    } else {
      // Just store as new if not found
      store(prompt, newResponse);
    }
  }

  /**
   * Batch store multiple prompt-response pairs.
   *
   * @param pairs List of prompt-response pairs
   */
  public void storeBatch(List<PromptResponsePair> pairs) {
    if (pairs == null || pairs.isEmpty()) {
      return;
    }

    // Prepare all documents
    List<Map<String, Object>> documents = new ArrayList<>();

    for (PromptResponsePair pair : pairs) {
      // Generate embedding for prompt
      float[] embedding = vectorizer.embed(pair.getPrompt());

      // Create document
      Map<String, Object> doc = new HashMap<>();
      doc.put(PROMPT_FIELD_NAME, pair.getPrompt());
      doc.put(RESPONSE_FIELD_NAME, pair.getResponse());
      doc.put(CACHE_VECTOR_FIELD_NAME, embedding);
      doc.put(INSERTED_AT_FIELD_NAME, Instant.now().getEpochSecond());
      doc.put(UPDATED_AT_FIELD_NAME, Instant.now().getEpochSecond());

      // Add metadata fields
      if (pair.getMetadata() != null) {
        for (Map.Entry<String, Object> entry : pair.getMetadata().entrySet()) {
          doc.put(entry.getKey(), entry.getValue());
        }
      }

      // Generate a unique ID for the document
      String entryId = UUID.randomUUID().toString();
      doc.put("id", entryId);

      documents.add(doc);
    }

    // Store all documents in batch
    List<String> keys = index.load(documents, "id");

    // Set TTL if configured - use pipeline for batch TTL setting
    if (ttl != null && ttl > 0 && !keys.isEmpty()) {
      try (var pipeline = redisClient.pipelined()) {
        for (String key : keys) {
          pipeline.expire(key, ttl);
        }
        pipeline.sync();
      }
    }
  }

  /**
   * Batch check multiple prompts.
   *
   * @param prompts List of prompts to check
   * @return List of optional cache hits
   */
  public List<Optional<CacheHit>> checkBatch(List<String> prompts) {
    return prompts.stream().map(this::check).collect(Collectors.toList());
  }

  /**
   * Clear cache with optional filter.
   *
   * @param filter Optional filter expression
   */
  public void clear(Filter filter) {
    if (filter == null) {
      clear();
    } else {
      // Search for matching documents
      Query searchQuery = new Query(filter.build());
      searchQuery.limit(0, 1000); // Get up to 1000 matches

      var searchResult = redisClient.ftSearch(index.getName(), searchQuery);

      // Collect all document IDs
      List<String> idsToDelete = new ArrayList<>();
      for (Document doc : searchResult.getDocuments()) {
        idsToDelete.add(doc.getId());
      }

      // Delete all matching documents in batch
      if (!idsToDelete.isEmpty()) {
        redisClient.del(idsToDelete.toArray(new String[0]));
      }
    }
  }

  /**
   * Get the current distance threshold.
   *
   * @return The distance threshold
   */
  public float getDistanceThreshold() {
    return distanceThreshold;
  }

  /**
   * Set the distance threshold for similarity matching.
   *
   * @param threshold The new threshold (0 = exact match, 1 = any match)
   */
  public void setDistanceThreshold(float threshold) {
    this.distanceThreshold = threshold;
  }

  /**
   * Get the hit count.
   *
   * @return Number of cache hits
   */
  public long getHitCount() {
    return hitCount.get();
  }

  /**
   * Get the miss count.
   *
   * @return Number of cache misses
   */
  public long getMissCount() {
    return missCount.get();
  }

  /**
   * Get the cache hit rate.
   *
   * @return Hit rate as a percentage (0.0 to 1.0)
   */
  public float getHitRate() {
    long total = hitCount.get() + missCount.get();
    if (total == 0) {
      return 0.0f;
    }
    return (float) hitCount.get() / total;
  }

  /** Reset statistics counters. */
  public void resetStatistics() {
    hitCount.set(0);
    missCount.set(0);
  }

  /** Builder for SemanticCache. */
  public static class Builder {
    private String name;
    private UnifiedJedis redisClient;
    private BaseVectorizer vectorizer;
    private float distanceThreshold = 0.2f;
    private Integer ttl;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder redisClient(UnifiedJedis redisClient) {
      this.redisClient = redisClient;
      return this;
    }

    public Builder vectorizer(BaseVectorizer vectorizer) {
      this.vectorizer = vectorizer;
      return this;
    }

    public Builder distanceThreshold(float threshold) {
      this.distanceThreshold = threshold;
      return this;
    }

    public Builder ttl(Integer ttl) {
      this.ttl = ttl;
      return this;
    }

    public SemanticCache build() {
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("Cache name is required");
      }
      if (redisClient == null) {
        throw new IllegalArgumentException("Redis client is required");
      }
      if (vectorizer == null) {
        throw new IllegalArgumentException("Vectorizer is required");
      }

      return new SemanticCache(this);
    }
  }
}
