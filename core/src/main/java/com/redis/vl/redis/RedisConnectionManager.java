package com.redis.vl.redis;

import java.io.Closeable;
import java.net.URI;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/** Manages Redis connections and provides connection pooling. */
@Slf4j
public class RedisConnectionManager implements Closeable {

  private final JedisPool jedisPool;

  /** Create a new connection manager with the given configuration */
  public RedisConnectionManager(RedisConnectionConfig config) {
    this.jedisPool = createJedisPool(config);
    log.info("Redis connection manager initialized");
  }

  /** Create a connection manager from a URI */
  public static RedisConnectionManager from(String uri) {
    return new RedisConnectionManager(RedisConnectionConfig.fromUri(uri));
  }

  /** Create a connection manager from host and port */
  public static RedisConnectionManager from(String host, int port) {
    return new RedisConnectionManager(RedisConnectionConfig.fromHostPort(host, port));
  }

  /** Create a connection manager from configuration */
  public static RedisConnectionManager from(RedisConnectionConfig config) {
    return new RedisConnectionManager(config);
  }

  /** Create JedisPool from configuration */
  private JedisPool createJedisPool(RedisConnectionConfig config) {
    JedisPoolConfig poolConfig = config.toJedisPoolConfig();

    if (config.getUri() != null) {
      URI uri = URI.create(config.getUri());
      return new JedisPool(
          poolConfig,
          uri.getHost(),
          uri.getPort() > 0 ? uri.getPort() : 6379,
          config.getConnectionTimeout());
    } else {
      return new JedisPool(
          poolConfig, config.getHost(), config.getPort(), config.getConnectionTimeout());
    }
  }

  /** Check if the connection manager is connected */
  public boolean isConnected() {
    return jedisPool != null && !jedisPool.isClosed();
  }

  /** Get a Jedis connection from the pool */
  public Jedis getJedis() {
    if (!isConnected()) {
      throw new IllegalStateException("Connection manager is not connected");
    }
    return jedisPool.getResource();
  }

  /**
   * Execute a command with a Jedis connection The connection is automatically returned to the pool
   * after execution
   */
  public <T> T execute(Function<Jedis, T> command) {
    try (Jedis jedis = getJedis()) {
      return command.apply(jedis);
    }
  }

  /** Execute a command without a return value */
  public void executeVoid(java.util.function.Consumer<Jedis> command) {
    try (Jedis jedis = getJedis()) {
      command.accept(jedis);
    }
  }

  /** Close the connection manager and release resources */
  @Override
  public void close() {
    if (jedisPool != null && !jedisPool.isClosed()) {
      jedisPool.close();
      log.info("Redis connection manager closed");
    }
  }
}
