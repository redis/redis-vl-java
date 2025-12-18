package com.redis.vl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Base class for SVS-VAMANA integration tests requiring Redis ≥ 8.2.0
 *
 * <p>Uses redis-stack:edge image which includes Redis 8.2.0+ with SVS-VAMANA support.
 *
 * <p>Updated for Jedis 7.2+ API using RedisClient instead of deprecated JedisPool/JedisPooled.
 */
public abstract class BaseSVSIntegrationTest {

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static UnifiedJedis unifiedJedis;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static String redisUrl;

  private static GenericContainer<?> redisContainer;
  private static RedisClient redisClient;

  @BeforeAll
  static void startContainer() {
    // Start Redis 8.2 container with SVS-VAMANA support
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:8.2")).withExposedPorts(6379);
    redisContainer.start();

    String host = redisContainer.getHost();
    int port = redisContainer.getMappedPort(6379);

    // Build Redis URL for testing URL-based constructors
    redisUrl = String.format("redis://%s:%d", host, port);

    // Create RedisClient using new Jedis 7.2+ API
    redisClient = RedisClient.create(host, port);

    // RedisClient extends UnifiedJedis, so we can use it for all operations
    unifiedJedis = redisClient;
  }

  @AfterAll
  static void stopContainer() {
    if (redisClient != null) {
      redisClient.close();
      redisClient = null;
      unifiedJedis = null;
    }
    if (redisContainer != null) {
      redisContainer.stop();
    }
  }
}
