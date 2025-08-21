package com.redis.vl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.UnifiedJedis;

/** Base class for integration tests with Redis Stack container */
public abstract class BaseIntegrationTest {

  private static GenericContainer<?> redisContainer;
  private static JedisPool jedisPool;
  protected static Jedis jedis;
  protected static UnifiedJedis unifiedJedis;
  protected static String redisUrl;

  @BeforeAll
  static void startContainer() {
    // Start Redis Stack container
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379);
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
