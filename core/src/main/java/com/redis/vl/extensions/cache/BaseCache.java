package com.redis.vl.extensions.cache;

import java.util.List;
import java.util.Set;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

/** Abstract base class for all cache implementations. */
public abstract class BaseCache {

  protected final String name;
  protected final String prefix;
  protected Integer ttl;
  protected UnifiedJedis redisClient;

  /**
   * Creates a new BaseCache instance.
   *
   * @param name The name of the cache
   * @param redisClient The Redis client connection
   * @param ttl Default time-to-live in seconds for cache entries (null for no expiration)
   */
  protected BaseCache(String name, UnifiedJedis redisClient, Integer ttl) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Cache name cannot be null or empty");
    }
    if (redisClient == null) {
      throw new IllegalArgumentException("Redis client cannot be null");
    }

    this.name = name;
    this.prefix = name + ":";
    this.redisClient = redisClient;
    this.ttl = ttl;
  }

  /** Creates a new BaseCache instance without TTL. */
  protected BaseCache(String name, UnifiedJedis redisClient) {
    this(name, redisClient, null);
  }

  /**
   * Generate a Redis key with the cache prefix.
   *
   * @param entryId The unique identifier for the cache entry
   * @return The prefixed Redis key
   */
  protected String makeKey(String entryId) {
    return prefix + entryId;
  }

  /**
   * Get the cache prefix.
   *
   * @return The cache prefix
   */
  public String getPrefix() {
    return prefix;
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
   * Get the default TTL for cache entries.
   *
   * @return Time-to-live in seconds (null if no expiration)
   */
  public Integer getTtl() {
    return ttl;
  }

  /**
   * Set the default TTL for cache entries.
   *
   * @param ttl Time-to-live in seconds (null for no expiration)
   */
  public void setTtl(Integer ttl) {
    this.ttl = ttl;
  }

  /**
   * Set expiration on a key.
   *
   * @param key The Redis key
   * @param ttl Time-to-live in seconds (uses default if null)
   */
  public void expire(String key, Integer ttl) {
    Integer effectiveTtl = ttl != null ? ttl : this.ttl;
    if (effectiveTtl != null && effectiveTtl > 0) {
      redisClient.expire(key, effectiveTtl);
    }
  }

  /** Clear all entries in the cache. */
  public void clear() {
    // Use SCAN to iterate through keys with our prefix
    String cursor = "0";
    ScanParams scanParams = new ScanParams();
    scanParams.match(prefix + "*");
    scanParams.count(100);

    do {
      ScanResult<String> scanResult = redisClient.scan(cursor, scanParams);
      List<String> keys = scanResult.getResult();

      if (!keys.isEmpty()) {
        redisClient.del(keys.toArray(new String[0]));
      }

      cursor = scanResult.getCursor();
    } while (!"0".equals(cursor));
  }

  /**
   * Get the number of entries in the cache.
   *
   * @return The number of cache entries
   */
  public long size() {
    Set<String> keys = redisClient.keys(prefix + "*");
    return keys.size();
  }

  /**
   * Check if the cache is connected to Redis.
   *
   * @return true if connected, false otherwise
   */
  public boolean isConnected() {
    try {
      return "PONG".equals(redisClient.ping());
    } catch (Exception e) {
      return false;
    }
  }

  /** Disconnect from Redis. */
  public void disconnect() {
    if (redisClient != null) {
      redisClient.close();
      redisClient = null;
    }
  }

  /** Helper method to set a value with optional TTL. */
  protected void setWithTtl(String key, String value, Integer ttl) {
    SetParams params = new SetParams();
    Integer effectiveTtl = ttl != null ? ttl : this.ttl;
    if (effectiveTtl != null && effectiveTtl > 0) {
      params.ex(effectiveTtl);
    }
    redisClient.set(key, value, params);
  }

  /** Helper method to set a byte array value with optional TTL. */
  protected void setWithTtl(byte[] key, byte[] value, Integer ttl) {
    if (ttl != null || this.ttl != null) {
      Integer effectiveTtl = ttl != null ? ttl : this.ttl;
      redisClient.setex(key, effectiveTtl, value);
    } else {
      redisClient.set(key, value);
    }
  }
}
