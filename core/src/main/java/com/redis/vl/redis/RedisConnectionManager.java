package com.redis.vl.redis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisSentinelClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Manages Redis connections using the modern Jedis 7.2+ API.
 *
 * <p>This class uses {@link RedisClient} for standalone connections and {@link RedisSentinelClient}
 * for Sentinel-managed high availability deployments.
 */
@Slf4j
public class RedisConnectionManager implements Closeable {

  private UnifiedJedis client;

  /**
   * Create a new connection manager with the given configuration.
   *
   * @param config The Redis connection configuration
   */
  public RedisConnectionManager(RedisConnectionConfig config) {
    this.client = createClient(config);
    log.info("Redis connection manager initialized with RedisClient");
  }

  /**
   * Create a new connection manager with Sentinel configuration.
   *
   * @param config The Sentinel connection configuration
   */
  public RedisConnectionManager(SentinelConfig config) {
    this.client = createSentinelClient(config);
    log.info("Redis Sentinel connection manager initialized with RedisSentinelClient");
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

  /** Create RedisClient from configuration using the new Jedis 7.2+ API */
  private UnifiedJedis createClient(RedisConnectionConfig config) {
    if (config.getUri() != null) {
      return RedisClient.create(config.getUri());
    }
    return RedisClient.builder().hostAndPort(config.getHost(), config.getPort()).build();
  }

  /** Create RedisSentinelClient from Sentinel configuration using the new Jedis 7.2+ API */
  private UnifiedJedis createSentinelClient(SentinelConfig config) {
    // Convert HostPort list to Set<HostAndPort>
    Set<HostAndPort> sentinelNodes =
        config.getSentinelHosts().stream()
            .map(hp -> new HostAndPort(hp.getHost(), hp.getPort()))
            .collect(Collectors.toSet());

    // Use RedisSentinelClient.builder() with the sentinels() method
    var builder =
        RedisSentinelClient.builder().masterName(config.getServiceName()).sentinels(sentinelNodes);

    // Add authentication if provided via clientConfig
    if (config.getPassword() != null) {
      builder.clientConfig(
          DefaultJedisClientConfig.builder()
              .user(config.getUsername())
              .password(config.getPassword())
              .build());
    }

    return builder.build();
  }

  /**
   * Check if the connection manager is connected.
   *
   * @return True if client is available, false otherwise
   */
  public boolean isConnected() {
    return client != null;
  }

  /**
   * Get the underlying UnifiedJedis client.
   *
   * <p>Since RedisClient extends UnifiedJedis, this provides full access to all Redis operations.
   *
   * @return The UnifiedJedis client
   * @throws IllegalStateException if the connection manager is not connected
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Callers need direct access to shared Redis client for operations")
  public UnifiedJedis getClient() {
    if (!isConnected()) {
      throw new IllegalStateException("Connection manager is not connected");
    }
    return client;
  }

  /**
   * Execute a command with the Redis client.
   *
   * @param <T> The return type of the command
   * @param command The function to execute with the UnifiedJedis client
   * @return The result of the command execution
   */
  public <T> T execute(Function<UnifiedJedis, T> command) {
    return command.apply(getClient());
  }

  /**
   * Execute a command without a return value.
   *
   * @param command The consumer to execute with the UnifiedJedis client
   */
  public void executeVoid(Consumer<UnifiedJedis> command) {
    command.accept(getClient());
  }

  /** Close the connection manager and release resources */
  @Override
  public void close() {
    if (client != null) {
      client.close();
      client = null;
      log.info("Redis connection manager closed");
    }
  }
}
