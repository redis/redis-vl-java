package com.redis.vl;

import com.redis.testcontainers.RedisStackContainer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Base class for integration tests with Redis container.
 *
 * <p>Updated for Jedis 7.2+ API using RedisClient instead of deprecated JedisPool/JedisPooled. Uses
 * Redis Stack image which includes all required modules (RediSearch, RedisJSON, etc.).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseIntegrationTest {

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static UnifiedJedis unifiedJedis;

  @SuppressFBWarnings(
      value = {"MS_PKGPROTECT", "MS_CANNOT_BE_FINAL"},
      justification = "Test infrastructure fields intentionally mutable for test lifecycle")
  protected static String redisUrl;

  @Container
  protected static final RedisStackContainer REDIS =
      new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag("latest"))
          .withReuse(true);

  private static RedisClient redisClient;

  @BeforeAll
  static void startContainer() {
    // Start container if not already started
    if (!REDIS.isRunning()) {
      REDIS.start();
    }

    String host = REDIS.getHost();
    int port = REDIS.getRedisPort();

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
  }

  /** Get Redis URL for tests that need URL-based connections */
  protected static String getRedisUri() {
    return redisUrl;
  }
}
