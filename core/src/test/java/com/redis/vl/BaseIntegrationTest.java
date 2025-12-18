package com.redis.vl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Base class for integration tests with Redis Stack container.
 *
 * <p>Updated for Jedis 7.2+ API using RedisClient instead of deprecated JedisPool/JedisPooled.
 */
public abstract class BaseIntegrationTest {

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static UnifiedJedis unifiedJedis;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static String redisUrl;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static GenericContainer<?> REDIS;

  private static RedisClient redisClient;

  @BeforeAll
  static void startContainer() {
    // Start Redis Stack container
    REDIS =
        new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379);
    REDIS.start();

    String host = REDIS.getHost();
    int port = REDIS.getMappedPort(6379);

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
    if (REDIS != null) {
      REDIS.stop();
    }
  }

  /** Get Redis URL for tests that need URL-based connections */
  protected static String getRedisUri() {
    return redisUrl;
  }
}
