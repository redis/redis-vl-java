package com.redis.vl.test;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Base class for all integration tests that require Redis. Provides a shared Redis Stack container
 * with TestContainers.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

  // Use Redis Stack for full RediSearch support
  @Container
  protected static final RedisStackContainer REDIS =
      new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag("latest"))
          .withReuse(true);

  static JedisPool jedisPool;
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

    // Create Jedis pool
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(50);
    poolConfig.setMaxIdle(10);
    poolConfig.setMinIdle(5);
    poolConfig.setTestOnBorrow(true);
    poolConfig.setTestOnReturn(true);
    poolConfig.setTestWhileIdle(true);

    jedisPool = new JedisPool(poolConfig, REDIS.getHost(), REDIS.getRedisPort());

    logger.info("Jedis pool created successfully");
  }

  @AfterAll
  static void teardownRedis() {
    if (jedisPool != null) {
      jedisPool.close();
      logger.info("Jedis pool closed");
    }
  }

  @BeforeEach
  void cleanupBeforeTest() {
    // Flush all data before each test for isolation
    try (var jedis = jedisPool.getResource()) {
      jedis.flushAll();
      logger.debug("Flushed all Redis data before test");
    }
  }

  /** Get the Redis URI for the test container */
  protected String getRedisUri() {
    return redisUri;
  }

  /** Get a Jedis connection from the pool */
  protected redis.clients.jedis.Jedis getJedis() {
    return jedisPool.getResource();
  }
}
