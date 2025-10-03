package com.redis.vl.redis;

import lombok.Builder;
import lombok.Getter;
import redis.clients.jedis.JedisPoolConfig;

/** Configuration for Redis connections. */
@Getter
@Builder
public class RedisConnectionConfig {

  /** Private constructor used by Lombok builder. */
  @SuppressWarnings("unused")
  private RedisConnectionConfig(
      String uri,
      String host,
      int port,
      int connectionTimeout,
      int socketTimeout,
      int maxTotal,
      int maxIdle,
      int minIdle,
      boolean testOnBorrow,
      boolean testOnReturn,
      boolean testWhileIdle) {
    this.uri = uri;
    this.host = host;
    this.port = port;
    this.connectionTimeout = connectionTimeout;
    this.socketTimeout = socketTimeout;
    this.maxTotal = maxTotal;
    this.maxIdle = maxIdle;
    this.minIdle = minIdle;
    this.testOnBorrow = testOnBorrow;
    this.testOnReturn = testOnReturn;
    this.testWhileIdle = testWhileIdle;
  }

  /** Redis connection URI (e.g., redis://localhost:6379) */
  private final String uri;

  /** Redis host (alternative to URI) */
  private final String host;

  /** Redis port (alternative to URI) */
  @Builder.Default private final int port = 6379;

  /** Connection timeout in milliseconds */
  @Builder.Default private final int connectionTimeout = 2000;

  /** Socket timeout in milliseconds */
  @Builder.Default private final int socketTimeout = 2000;

  /** Maximum total connections in the pool */
  @Builder.Default private final int maxTotal = 50;

  /** Maximum idle connections in the pool */
  @Builder.Default private final int maxIdle = 10;

  /** Minimum idle connections in the pool */
  @Builder.Default private final int minIdle = 5;

  /** Whether to test connections on borrow */
  @Builder.Default private final boolean testOnBorrow = true;

  /** Whether to test connections on return */
  @Builder.Default private final boolean testOnReturn = true;

  /** Whether to test connections while idle */
  @Builder.Default private final boolean testWhileIdle = true;

  /**
   * Create a default configuration with URI.
   *
   * @param uri Redis connection URI (e.g., redis://localhost:6379)
   * @return RedisConnectionConfig with specified URI
   */
  public static RedisConnectionConfig fromUri(String uri) {
    return RedisConnectionConfig.builder().uri(uri).build();
  }

  /**
   * Create a default configuration with host and port.
   *
   * @param host Redis host
   * @param port Redis port
   * @return RedisConnectionConfig with specified host and port
   */
  public static RedisConnectionConfig fromHostPort(String host, int port) {
    return RedisConnectionConfig.builder().host(host).port(port).build();
  }

  /**
   * Create a JedisPoolConfig from this configuration.
   *
   * @return JedisPoolConfig with settings from this configuration
   */
  public JedisPoolConfig toJedisPoolConfig() {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(maxTotal);
    config.setMaxIdle(maxIdle);
    config.setMinIdle(minIdle);
    config.setTestOnBorrow(testOnBorrow);
    config.setTestOnReturn(testOnReturn);
    config.setTestWhileIdle(testWhileIdle);
    return config;
  }
}
