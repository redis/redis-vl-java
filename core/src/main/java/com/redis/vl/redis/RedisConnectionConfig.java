package com.redis.vl.redis;

import lombok.Builder;
import lombok.Getter;
import redis.clients.jedis.JedisPoolConfig;

/** Configuration for Redis connections. */
@Getter
@Builder
public class RedisConnectionConfig {

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

  /** Create a JedisPoolConfig from this configuration */
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

  /** Create a default configuration with URI */
  public static RedisConnectionConfig fromUri(String uri) {
    return RedisConnectionConfig.builder().uri(uri).build();
  }

  /** Create a default configuration with host and port */
  public static RedisConnectionConfig fromHostPort(String host, int port) {
    return RedisConnectionConfig.builder().host(host).port(port).build();
  }
}
