package com.redis.vl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/** Base class for integration tests with Redis 8.x+ container */
public abstract class BaseIntegrationTest {

  protected static UnifiedJedis jedis;
  protected static String redisUrl;

  private static GenericContainer<?> redisContainer;

  @BeforeAll
  static void startContainer() {
    // Start Redis container (8.x+ includes search modules natively)
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379);
    redisContainer.start();

    String host = redisContainer.getHost();
    int port = redisContainer.getMappedPort(6379);

    // Build Redis URL for testing URL-based constructors
    redisUrl = String.format("redis://%s:%d", host, port);

    // Create Redis client using new Jedis 7.2 API
    jedis = RedisClient.create(host, port);
  }

  @AfterAll
  static void stopContainer() {
    if (jedis != null) {
      jedis.close();
    }
    if (redisContainer != null) {
      redisContainer.stop();
    }
  }
}
