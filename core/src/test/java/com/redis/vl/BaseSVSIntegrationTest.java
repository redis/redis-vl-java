package com.redis.vl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.*;

/**
 * Base class for SVS-VAMANA integration tests requiring Redis ≥ 8.2.0
 *
 * <p>Uses redis-stack:edge image which includes Redis 8.2.0+ with SVS-VAMANA support.
 */
public abstract class BaseSVSIntegrationTest {

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static Jedis jedis;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static UnifiedJedis unifiedJedis;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static String redisUrl;

  private static GenericContainer<?> redisContainer;
  private static JedisPool jedisPool;

  @BeforeAll
  static void startContainer() {
    // Start Redis 8.2 container with SVS-VAMANA support
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:8.2")).withExposedPorts(6379);
    redisContainer.start();

    // Create Jedis connection pool
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);

    String host = redisContainer.getHost();
    int port = redisContainer.getMappedPort(6379);

    // Build Redis URL for testing URL-based constructors
    redisUrl = String.format("redis://%s:%d", host, port);

    jedisPool = new JedisPool(poolConfig, host, port);

    jedis = jedisPool.getResource();

    // Create UnifiedJedis for RediSearch operations
    HostAndPort hostAndPort = new HostAndPort(host, port);
    unifiedJedis = new UnifiedJedis(hostAndPort);
  }

  @AfterAll
  static void stopContainer() {
    if (jedis != null) {
      jedis.close();
    }
    if (unifiedJedis != null) {
      unifiedJedis.close();
    }
    if (jedisPool != null) {
      jedisPool.close();
    }
    if (redisContainer != null) {
      redisContainer.stop();
    }
  }
}
