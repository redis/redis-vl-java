package com.redis.vl.redis;

import java.io.Closeable;
import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.util.Pool;

/** Manages Redis connections and provides connection pooling. */
@Slf4j
public class RedisConnectionManager implements Closeable {

  private final Pool<Jedis> jedisPool;

  /**
   * Create a new connection manager with the given configuration.
   *
   * @param config The Redis connection configuration
   */
  public RedisConnectionManager(RedisConnectionConfig config) {
    this.jedisPool = createJedisPool(config);
    log.info("Redis connection manager initialized");
  }

  /**
   * Create a new connection manager with Sentinel configuration.
   *
   * @param config The Sentinel connection configuration
   */
  public RedisConnectionManager(SentinelConfig config) {
    this.jedisPool = createJedisSentinelPool(config);
    log.info("Redis Sentinel connection manager initialized");
  }

  /**
   * Create a connection manager from a URI.
   *
   * <p>Supports both standard Redis URLs and Sentinel URLs:
   *
   * <ul>
   *   <li>redis://[username:password@]host:port[/database] - Standard Redis connection
   *   <li>redis+sentinel://[username:password@]host1:port1,host2:port2/service_name[/database] -
   *       Sentinel connection
   * </ul>
   *
   * @param uri The Redis connection URI
   * @return A new RedisConnectionManager instance
   */
  public static RedisConnectionManager from(String uri) {
    if (uri != null && uri.startsWith("redis+sentinel://")) {
      return new RedisConnectionManager(SentinelConfig.fromUrl(uri));
    }
    return new RedisConnectionManager(RedisConnectionConfig.fromUri(uri));
  }

  /**
   * Create a connection manager from host and port.
   *
   * @param host The Redis host
   * @param port The Redis port
   * @return A new RedisConnectionManager instance
   */
  public static RedisConnectionManager from(String host, int port) {
    return new RedisConnectionManager(RedisConnectionConfig.fromHostPort(host, port));
  }

  /**
   * Create a connection manager from configuration.
   *
   * @param config The Redis connection configuration
   * @return A new RedisConnectionManager instance
   */
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

  /** Create JedisSentinelPool from Sentinel configuration */
  private JedisSentinelPool createJedisSentinelPool(SentinelConfig config) {
    // Convert HostPort list to Set<String> in "host:port" format
    Set<String> sentinelHosts =
        config.getSentinelHosts().stream()
            .map(hp -> hp.getHost() + ":" + hp.getPort())
            .collect(Collectors.toSet());

    // Create pool config with defaults
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);
    poolConfig.setTestOnBorrow(true);

    // Create Sentinel pool
    return new JedisSentinelPool(
        config.getServiceName(),
        sentinelHosts,
        poolConfig,
        config.getConnectionTimeout(),
        config.getSocketTimeout(),
        config.getUsername(),
        config.getPassword(),
        config.getDatabase() != null ? config.getDatabase() : 0,
        null); // clientName
  }

  /**
   * Check if the connection manager is connected.
   *
   * @return True if connected and pool is not closed, false otherwise
   */
  public boolean isConnected() {
    return jedisPool != null && !jedisPool.isClosed();
  }

  /**
   * Get a Jedis connection from the pool.
   *
   * @return A Jedis connection from the pool
   * @throws IllegalStateException if the connection manager is not connected
   */
  public Jedis getJedis() {
    if (!isConnected()) {
      throw new IllegalStateException("Connection manager is not connected");
    }
    return jedisPool.getResource();
  }

  /**
   * Execute a command with a Jedis connection. The connection is automatically returned to the pool
   * after execution.
   *
   * @param <T> The return type of the command
   * @param command The function to execute with the Jedis connection
   * @return The result of the command execution
   */
  public <T> T execute(Function<Jedis, T> command) {
    try (Jedis jedis = getJedis()) {
      return command.apply(jedis);
    }
  }

  /**
   * Execute a command without a return value.
   *
   * @param command The consumer to execute with the Jedis connection
   */
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
