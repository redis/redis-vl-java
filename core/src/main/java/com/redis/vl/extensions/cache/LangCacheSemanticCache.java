package com.redis.vl.extensions.cache;

import static com.redis.vl.utils.Utils.denormCosineDistance;
import static com.redis.vl.utils.Utils.normCosineDistance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM Cache implementation using the LangCache managed service.
 *
 * <p>This cache uses the LangCache API service for semantic caching of LLM responses. It requires a
 * LangCache account and API key.
 *
 * <p>Port of redis-vl-python/redisvl/extensions/cache/llm/langcache.py
 *
 * <p>Example:
 *
 * <pre>{@code
 * LangCacheSemanticCache cache = new LangCacheSemanticCache.Builder()
 *     .name("my_cache")
 *     .serverUrl("https://aws-us-east-1.langcache.redis.io")
 *     .cacheId("your-cache-id")
 *     .apiKey("your-api-key")
 *     .ttl(3600)
 *     .build();
 *
 * // Store a response
 * cache.store(
 *     "What is the capital of France?",
 *     "Paris",
 *     Map.of("topic", "geography")
 * );
 *
 * // Check for cached responses
 * List<Map<String, Object>> results = cache.check("What is the capital of France?");
 * }</pre>
 */
public class LangCacheSemanticCache {

  private static final Logger logger = LoggerFactory.getLogger(LangCacheSemanticCache.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  /**
   * URL percent-encoding for attribute values.
   *
   * <p>LangCache service rejects or mishandles certain characters in attribute values:
   *
   * <ul>
   *   <li>Comma (,) - U+002C: Rejected by service validation
   *   <li>Forward slash (/) - U+002F: May not reliably match in filters
   *   <li>Backslash (\) - U+005C: Causes encoding issues
   *   <li>Question mark (?) - U+003F: Causes filtering failures
   * </ul>
   *
   * <p>We use standard URL percent-encoding (RFC 3986) to handle these characters, replacing the
   * previous fullwidth Unicode character approach. This provides better compatibility and follows
   * standard web encoding practices.
   *
   * <p>Port of redis-vl-python PR #442
   */

  /**
   * Encode a string attribute value for use with the LangCache service using URL percent-encoding.
   *
   * <p>Uses standard URL percent-encoding (RFC 3986) to encode problematic characters (comma,
   * slash, backslash, question mark). This keeps attribute values round-trippable and usable for
   * attribute filtering.
   *
   * <p>URLEncoder.encode() with UTF-8 charset ensures all special characters are properly escaped
   * as %XX hex sequences (e.g., comma becomes %2C, slash becomes %2F).
   *
   * @param value The original attribute value
   * @return The URL percent-encoded value safe for LangCache
   */
  private static String encodeAttributeValue(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    // URLEncoder.encode with UTF-8 uses application/x-www-form-urlencoded format
    // which replaces spaces with '+'. We want percent-encoding (RFC 3986) which uses %20.
    // The quote() function in Python uses percent-encoding with safe='', so we match that.
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Encode attribute map for LangCache service.
   *
   * <p>Returns a copy of attributes with string values safely encoded. Only top-level string values
   * are encoded; non-string values are left unchanged. If no values require encoding, the original
   * map is returned unchanged.
   *
   * @param attributes The original attributes
   * @return Encoded attributes (may be same instance if no encoding needed)
   */
  private static Map<String, Object> encodeAttributes(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return attributes;
    }

    Map<String, Object> encoded = null; // Lazy allocation

    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String) {
        String originalStr = (String) value;
        String encodedStr = encodeAttributeValue(originalStr);

        if (!encodedStr.equals(originalStr)) {
          // First change found - allocate map and copy all entries so far
          if (encoded == null) {
            encoded = new HashMap<>(attributes);
          }
          encoded.put(entry.getKey(), encodedStr);
        }
      }
    }

    return encoded != null ? encoded : attributes;
  }

  /**
   * Decode a string attribute value returned from the LangCache service using URL percent-decoding.
   *
   * <p>Reverses {@link #encodeAttributeValue}, translating percent-encoded sequences back to their
   * original characters so callers see the original values they stored.
   *
   * <p>URLDecoder.decode() with UTF-8 charset converts %XX hex sequences back to original
   * characters (e.g., %2C becomes comma, %2F becomes slash).
   *
   * @param value The URL percent-encoded attribute value from LangCache
   * @return The decoded original value
   */
  private static String decodeAttributeValue(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    // URLDecoder.decode reverses the encoding applied by URLEncoder.encode
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  /**
   * Decode attribute map from LangCache service.
   *
   * <p>Returns a copy of attributes with string values safely decoded. This is the inverse of
   * {@link #encodeAttributes}. Only top-level string values are decoded; non-string values are left
   * unchanged. If no values require decoding, the original map is returned unchanged.
   *
   * @param attributes The encoded attributes from LangCache
   * @return Decoded attributes (may be same instance if no decoding needed)
   */
  private static Map<String, Object> decodeAttributes(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return attributes;
    }

    Map<String, Object> decoded = null; // Lazy allocation

    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String) {
        String originalStr = (String) value;
        String decodedStr = decodeAttributeValue(originalStr);

        if (!decodedStr.equals(originalStr)) {
          // First change found - allocate map and copy all entries so far
          if (decoded == null) {
            decoded = new HashMap<>(attributes);
          }
          decoded.put(entry.getKey(), decodedStr);
        }
      }
    }

    return decoded != null ? decoded : attributes;
  }

  private final String name;
  private final String serverUrl;
  private final String cacheId;
  private final String apiKey;
  private final Integer ttl;
  private final List<String> searchStrategies;
  private final String distanceScale;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  private LangCacheSemanticCache(Builder builder) {
    this.name = builder.name;
    this.serverUrl = builder.serverUrl;
    this.cacheId = builder.cacheId;
    this.apiKey = builder.apiKey;
    this.ttl = builder.ttl;
    this.searchStrategies = builder.searchStrategies;
    this.distanceScale = builder.distanceScale;
    this.httpClient = builder.httpClient != null ? builder.httpClient : new OkHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Get the cache name.
   *
   * @return The cache name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the TTL for cache entries.
   *
   * @return Time-to-live in seconds (null if no expiration)
   */
  public Integer getTtl() {
    return ttl;
  }

  /**
   * Convert distance threshold to similarity threshold based on distance scale.
   *
   * @param distanceThreshold The distance threshold (null for no threshold)
   * @return The similarity threshold for LangCache API
   */
  private Float similarityThreshold(Float distanceThreshold) {
    if (distanceThreshold == null) {
      return null;
    }
    if ("redis".equals(distanceScale)) {
      return normCosineDistance(distanceThreshold);
    }
    return 1.0f - distanceThreshold;
  }

  /**
   * Convert LangCache result to CacheHit representation.
   *
   * @param result The result from LangCache API
   * @return Map representation of cache hit
   */
  private Map<String, Object> convertToCacheHit(JsonNode result) {
    Map<String, Object> hit = new HashMap<>();

    hit.put("entry_id", result.path("id").asText(""));
    hit.put("prompt", result.path("prompt").asText(""));
    hit.put("response", result.path("response").asText(""));

    // LangCache returns similarity (higher is better), convert to distance (lower is better)
    float similarity = (float) result.path("similarity").asDouble(0.0);
    float distance;
    if ("redis".equals(distanceScale)) {
      distance = denormCosineDistance(similarity); // -> [0,2]
    } else {
      distance = 1.0f - similarity; // normalized [0,1]
    }
    hit.put("vector_distance", distance);

    hit.put("inserted_at", result.path("created_at").asDouble(0.0));
    hit.put("updated_at", result.path("updated_at").asDouble(0.0));

    // Extract attributes as metadata
    JsonNode attributes = result.path("attributes");
    if (!attributes.isMissingNode() && !attributes.isNull()) {
      Map<String, Object> metadata =
          objectMapper.convertValue(attributes, new TypeReference<Map<String, Object>>() {});
      if (!metadata.isEmpty()) {
        // Decode attribute values that were encoded for LangCache so callers
        // see the original metadata values they stored (PR #437)
        metadata = decodeAttributes(metadata);
        hit.put("metadata", metadata);
      }
    }

    return hit;
  }

  /**
   * Store a prompt-response pair in the cache.
   *
   * @param prompt The user prompt to cache
   * @param response The LLM response to cache
   * @param metadata Optional metadata (stored as attributes in LangCache)
   * @return The entry ID for the cached entry
   * @throws IOException If the API request fails
   */
  public String store(String prompt, String response, Map<String, Object> metadata)
      throws IOException {
    return store(prompt, response, metadata, null);
  }

  /**
   * Store a prompt-response pair in the cache with a per-entry TTL.
   *
   * <p>Port of redis-vl-python PR #442
   *
   * @param prompt The user prompt to cache
   * @param response The LLM response to cache
   * @param metadata Optional metadata (stored as attributes in LangCache)
   * @param ttl Time-to-live in seconds (null for default TTL)
   * @return The entry ID for the cached entry
   * @throws IOException If the API request fails
   */
  public String store(String prompt, String response, Map<String, Object> metadata, Integer ttl)
      throws IOException {
    if (prompt == null || prompt.isEmpty()) {
      throw new IllegalArgumentException("prompt is required");
    }
    if (response == null || response.isEmpty()) {
      throw new IllegalArgumentException("response is required");
    }

    // Build request body
    ObjectNode requestBody = objectMapper.createObjectNode();
    requestBody.put("prompt", prompt);
    requestBody.put("response", response);

    if (metadata != null && !metadata.isEmpty()) {
      // Encode all string attribute values so they are accepted by the
      // LangCache service and remain filterable (PR #442)
      Map<String, Object> safeMetadata = encodeAttributes(metadata);
      requestBody.set("attributes", objectMapper.valueToTree(safeMetadata));
    }

    // Add per-entry TTL if specified (convert seconds to milliseconds)
    // Port of redis-vl-python PR #442
    if (ttl != null) {
      int ttlMillis = Math.round(ttl * 1000.0f);
      requestBody.put("ttl_millis", ttlMillis);
    }

    // Make API request
    String storeUrl = serverUrl + "/v1/caches/" + cacheId + "/entries";
    logger.info(
        "LangCache store: '{}' to {}",
        prompt.substring(0, Math.min(50, prompt.length())),
        storeUrl);

    Request request =
        new Request.Builder()
            .url(storeUrl)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

    try (Response httpResponse = httpClient.newCall(request).execute()) {
      logger.info("LangCache store response: {} {}", httpResponse.code(), httpResponse.message());
      if (!httpResponse.isSuccessful()) {
        throw new IOException("Unexpected response: " + httpResponse);
      }

      ResponseBody body = httpResponse.body();
      if (body == null) {
        throw new IOException("Empty response body");
      }
      String responseBody = body.string();
      JsonNode jsonResponse = objectMapper.readTree(responseBody);

      return jsonResponse.path("entry_id").asText("");
    }
  }

  /**
   * Check the cache for semantically similar prompts.
   *
   * @param prompt The text prompt to search for
   * @param attributes LangCache attributes to filter by
   * @param numResults Number of results to return
   * @param returnFields Not used (for compatibility)
   * @param filterExpression Not supported by LangCache
   * @param distanceThreshold Maximum distance threshold (converted based on distance_scale)
   * @return List of matching cache entries
   * @throws IOException If the API request fails
   */
  public List<Map<String, Object>> check(
      String prompt,
      Map<String, Object> attributes,
      int numResults,
      List<String> returnFields,
      Object filterExpression,
      Float distanceThreshold)
      throws IOException {

    if (prompt == null || prompt.isEmpty()) {
      throw new IllegalArgumentException("prompt is required for LangCache search");
    }

    if (filterExpression != null) {
      logger.warn("LangCache does not support filter expressions");
    }

    // Build request body
    ObjectNode requestBody = objectMapper.createObjectNode();
    requestBody.put("prompt", prompt);

    // Add search strategies
    ArrayNode strategiesArray = objectMapper.createArrayNode();
    for (String strategy : searchStrategies) {
      strategiesArray.add(strategy);
    }
    requestBody.set("search_strategies", strategiesArray);

    // Convert distance threshold to similarity threshold
    Float similarityThreshold = similarityThreshold(distanceThreshold);
    if (similarityThreshold != null) {
      requestBody.put("similarity_threshold", similarityThreshold);
    }

    // Add attributes if provided
    if (attributes != null && !attributes.isEmpty()) {
      // Encode all string attribute values so they are accepted by the
      // LangCache service and remain filterable (PR #437 & #438)
      Map<String, Object> safeAttributes = encodeAttributes(attributes);
      requestBody.set("attributes", objectMapper.valueToTree(safeAttributes));
    }

    // Make API request
    String searchUrl = serverUrl + "/v1/caches/" + cacheId + "/entries/search";
    logger.debug("LangCache search: {} to {}", prompt, searchUrl);

    Request request =
        new Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

    try (Response httpResponse = httpClient.newCall(request).execute()) {
      logger.debug("LangCache search response: {} {}", httpResponse.code(), httpResponse.message());
      if (!httpResponse.isSuccessful()) {
        throw new IOException("Unexpected response: " + httpResponse);
      }

      ResponseBody body = httpResponse.body();
      if (body == null) {
        throw new IOException("Empty response body");
      }
      String responseBody = body.string();
      JsonNode jsonResponse = objectMapper.readTree(responseBody);

      // Extract results
      List<Map<String, Object>> hits = new ArrayList<>();
      JsonNode dataArray = jsonResponse.path("data");
      if (dataArray.isArray()) {
        int count = 0;
        for (JsonNode result : dataArray) {
          if (count >= numResults) {
            break;
          }
          hits.add(convertToCacheHit(result));
          count++;
        }
      }

      return hits;
    }
  }

  /**
   * Delete the entire cache.
   *
   * <p>This deletes all entries in the cache by calling the flush API.
   *
   * @throws IOException If the API request fails
   */
  public void delete() throws IOException {
    flush();
  }

  /**
   * Flush the entire cache.
   *
   * <p>This deletes all entries in the cache using the dedicated flush endpoint.
   *
   * @throws IOException If the API request fails
   */
  public void flush() throws IOException {
    Request request =
        new Request.Builder()
            .url(serverUrl + "/v1/caches/" + cacheId + "/flush")
            .addHeader("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create("", null))
            .build();

    try (Response httpResponse = httpClient.newCall(request).execute()) {
      if (!httpResponse.isSuccessful()) {
        throw new IOException("Unexpected response: " + httpResponse);
      }
    }
  }

  /**
   * Clear the cache of all entries.
   *
   * <p>This is an alias for delete() to match the BaseCache interface.
   *
   * @throws IOException If the API request fails
   */
  public void clear() throws IOException {
    delete();
  }

  /**
   * Delete a single cache entry by ID.
   *
   * @param entryId The ID of the entry to delete
   * @throws IOException If the API request fails
   */
  public void deleteById(String entryId) throws IOException {
    Request request =
        new Request.Builder()
            .url(serverUrl + "/v1/caches/" + cacheId + "/entries/" + entryId)
            .addHeader("Authorization", "Bearer " + apiKey)
            .delete()
            .build();

    try (Response httpResponse = httpClient.newCall(request).execute()) {
      if (!httpResponse.isSuccessful()) {
        throw new IOException("Unexpected response: " + httpResponse);
      }
    }
  }

  /**
   * Delete cache entries matching the given attributes.
   *
   * @param attributes Attributes to match for deletion
   * @return Result of the deletion operation
   * @throws IOException If the API request fails
   */
  public Map<String, Object> deleteByAttributes(Map<String, Object> attributes) throws IOException {
    // Return early if attributes is empty - avoid sending request with empty attributes
    if (attributes == null || attributes.isEmpty()) {
      Map<String, Object> emptyResult = new HashMap<>();
      emptyResult.put("deleted_entries_count", 0);
      return emptyResult;
    }

    // Build request body
    ObjectNode requestBody = objectMapper.createObjectNode();
    // Encode all string attribute values so they match what was stored (PR #437 & #438)
    Map<String, Object> safeAttributes = encodeAttributes(attributes);
    requestBody.set("attributes", objectMapper.valueToTree(safeAttributes));

    Request request =
        new Request.Builder()
            .url(serverUrl + "/v1/caches/" + cacheId + "/query/delete")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

    try (Response httpResponse = httpClient.newCall(request).execute()) {
      if (!httpResponse.isSuccessful()) {
        throw new IOException("Unexpected response: " + httpResponse);
      }

      ResponseBody body = httpResponse.body();
      if (body == null) {
        throw new IOException("Empty response body");
      }
      String responseBody = body.string();
      JsonNode jsonResponse = objectMapper.readTree(responseBody);

      return objectMapper.convertValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
    }
  }

  /**
   * Update specific fields within an existing cache entry.
   *
   * <p>Note: LangCache API does not support updating individual entries. This method will throw
   * UnsupportedOperationException.
   *
   * @param key The key of the document to update
   * @param field The field to update
   * @param value The new value
   * @throws UnsupportedOperationException LangCache does not support entry updates
   */
  public void update(String key, String field, Object value) {
    throw new UnsupportedOperationException(
        "LangCache API does not support updating individual entries. "
            + "Delete and re-create the entry instead.");
  }

  /** Builder for LangCacheSemanticCache. */
  public static class Builder {
    private String name = "langcache";
    private String serverUrl = "https://aws-us-east-1.langcache.redis.io";
    private String cacheId;
    private String apiKey;
    private Integer ttl;
    private boolean useExactSearch = true;
    private boolean useSemanticSearch = true;
    private String distanceScale = "normalized";
    private List<String> searchStrategies;
    private OkHttpClient httpClient;

    /** Create a new Builder instance */
    public Builder() {
      // Default constructor
    }

    /**
     * Set the cache name
     *
     * @param name Cache name
     * @return This builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the LangCache server URL
     *
     * @param serverUrl Server URL
     * @return This builder
     */
    public Builder serverUrl(String serverUrl) {
      this.serverUrl = serverUrl;
      return this;
    }

    /**
     * Set the LangCache cache ID
     *
     * @param cacheId Cache ID
     * @return This builder
     */
    public Builder cacheId(String cacheId) {
      this.cacheId = cacheId;
      return this;
    }

    /**
     * Set the LangCache API key
     *
     * @param apiKey API key
     * @return This builder
     */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /**
     * Set the TTL for cache entries
     *
     * @param ttl Time-to-live in seconds
     * @return This builder
     */
    public Builder ttl(Integer ttl) {
      this.ttl = ttl;
      return this;
    }

    /**
     * Set whether to use exact search
     *
     * @param useExactSearch Whether to use exact matching
     * @return This builder
     */
    public Builder useExactSearch(boolean useExactSearch) {
      this.useExactSearch = useExactSearch;
      return this;
    }

    /**
     * Set whether to use semantic search
     *
     * @param useSemanticSearch Whether to use semantic search
     * @return This builder
     */
    public Builder useSemanticSearch(boolean useSemanticSearch) {
      this.useSemanticSearch = useSemanticSearch;
      return this;
    }

    /**
     * Set the distance scale for distance thresholds
     *
     * @param distanceScale Either "normalized" (0-1) or "redis" (0-2 COSINE)
     * @return This builder
     */
    public Builder distanceScale(String distanceScale) {
      this.distanceScale = distanceScale;
      return this;
    }

    /**
     * Set the HTTP client (primarily for testing)
     *
     * @param httpClient OkHttp client
     * @return This builder
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "OkHttpClient is intentionally shared for testing purposes")
    public Builder httpClient(OkHttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    /**
     * Build the LangCacheSemanticCache
     *
     * @return LangCacheSemanticCache instance
     */
    public LangCacheSemanticCache build() {
      // Validate required parameters
      if (cacheId == null || cacheId.isEmpty()) {
        throw new IllegalArgumentException("cache_id is required for LangCacheSemanticCache");
      }
      if (apiKey == null || apiKey.isEmpty()) {
        throw new IllegalArgumentException("api_key is required for LangCacheSemanticCache");
      }

      // Validate distance scale
      if (!"normalized".equals(distanceScale) && !"redis".equals(distanceScale)) {
        throw new IllegalArgumentException("distance_scale must be 'normalized' or 'redis'");
      }

      // Build search strategies list
      searchStrategies = new ArrayList<>();
      if (useExactSearch) {
        searchStrategies.add("exact");
      }
      if (useSemanticSearch) {
        searchStrategies.add("semantic");
      }

      if (searchStrategies.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one of useExactSearch or useSemanticSearch must be true");
      }

      return new LangCacheSemanticCache(this);
    }
  }
}
