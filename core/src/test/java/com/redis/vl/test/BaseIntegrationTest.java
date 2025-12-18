package com.redis.vl.test;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Base class for all integration tests that require Redis. Provides a shared Redis Stack container
 * with TestContainers.
 *
 * <p>Updated for Jedis 7.2+ API using RedisClient instead of deprecated JedisPool/JedisPooled.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

  // Use Redis Stack for full RediSearch support
  @Container
  protected static final RedisStackContainer REDIS =
      new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag("latest"))
          .withReuse(true);

  static RedisClient redisClient;
  static String redisUri;

  @BeforeAll
  static void setupRedis() {
    // Start container if not already started
    if (!REDIS.isRunning()) {
      REDIS.start();
    }

    // Build Redis URI
    redisUri = String.format("redis://%s:%d", REDIS.getHost(), REDIS.getRedisPort());

    logger.info("Redis Stack container started at: {}", redisUri);

    // Create RedisClient using new Jedis 7.2+ API
    redisClient = RedisClient.create(REDIS.getHost(), REDIS.getRedisPort());

    logger.info("RedisClient created successfully");
  }

  @AfterAll
  static void teardownRedis() {
    if (redisClient != null) {
      redisClient.close();
      redisClient = null;
      logger.info("RedisClient closed");
    }
  }

  @BeforeEach
  void cleanupBeforeTest() {
    // Flush all data before each test for isolation
    if (redisClient != null) {
      redisClient.flushAll();
      logger.debug("Flushed all Redis data before test");
    }
  }

  /** Get the Redis URI for the test container */
  protected String getRedisUri() {
    return redisUri;
  }

  /** Get the Redis client */
  protected UnifiedJedis getRedisClient() {
    return redisClient;
  }
}
