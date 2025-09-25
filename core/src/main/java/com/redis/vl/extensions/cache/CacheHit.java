package com.redis.vl.extensions.cache;

import java.util.Map;

/**
 * Represents a cache hit from SemanticCache. Contains the matched response and metadata about the
 * match.
 */
public class CacheHit {

  private final String prompt;
  private final String response;
  private final float distance;
  private final Map<String, Object> metadata;

  /**
   * Creates a new CacheHit.
   *
   * @param prompt The original prompt that was matched
   * @param response The cached response
   * @param distance The vector distance from the query
   * @param metadata Additional metadata stored with the cache entry
   */
  public CacheHit(String prompt, String response, float distance, Map<String, Object> metadata) {
    this.prompt = prompt;
    this.response = response;
    this.distance = distance;
    this.metadata = metadata;
  }

  /**
   * Get the matched prompt.
   *
   * @return The prompt
   */
  public String getPrompt() {
    return prompt;
  }

  /**
   * Get the cached response.
   *
   * @return The response
   */
  public String getResponse() {
    return response;
  }

  /**
   * Get the vector distance.
   *
   * @return The distance (0 = exact match, higher = less similar)
   */
  public float getDistance() {
    return distance;
  }

  /**
   * Get the metadata.
   *
   * @return The metadata map
   */
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  @Override
  public String toString() {
    return String.format(
        "CacheHit{prompt='%s', response='%s', distance=%.4f}", prompt, response, distance);
  }
}
