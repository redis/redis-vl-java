package com.redis.vl.test.vcr;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPooled;

/**
 * Manages VCR state and resources throughout a test session.
 *
 * <p>VCRContext handles:
 *
 * <ul>
 *   <li>Redis container lifecycle with persistence
 *   <li>Test context tracking
 *   <li>Call counter management for cassette key generation
 *   <li>Statistics collection
 * </ul>
 */
public class VCRContext {

  private final VCRTest config;
  private final Path dataDir;

  private GenericContainer<?> redisContainer;
  private JedisPooled jedis;
  private VCRRegistry registry;

  private String currentTestId;
  private VCRMode effectiveMode;
  private final List<String> currentCassetteKeys = new ArrayList<>();
  private final Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

  // Statistics
  private final AtomicLong cacheHits = new AtomicLong();
  private final AtomicLong cacheMisses = new AtomicLong();
  private final AtomicLong apiCalls = new AtomicLong();

  /**
   * Creates a new VCR context with the given configuration.
   *
   * @param config the VCR test configuration
   */
  public VCRContext(VCRTest config) {
    this.config = config;
    this.dataDir = Path.of(config.dataDir());
    this.effectiveMode = config.mode();
  }

  /**
   * Initializes the VCR context, starting Redis and loading existing cassettes.
   *
   * @throws Exception if initialization fails
   */
  public void initialize() throws Exception {
    // Ensure data directory exists
    Files.createDirectories(dataDir);

    // Start Redis container with persistence
    startRedis();

    // Initialize registry
    registry = new VCRRegistry(jedis);
  }

  /** Starts the Redis container with appropriate persistence configuration. */
  @SuppressWarnings("resource")
  private void startRedis() {
    String redisCommand = buildRedisCommand();

    redisContainer =
        new GenericContainer<>(DockerImageName.parse(config.redisImage()))
            .withExposedPorts(6379)
            .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE)
            .withCommand(redisCommand);

    redisContainer.start();

    String host = redisContainer.getHost();
    Integer port = redisContainer.getFirstMappedPort();
    jedis = new JedisPooled(host, port);

    // Wait for Redis to be ready and load existing data
    waitForRedis();
  }

  private String buildRedisCommand() {
    StringBuilder cmd = new StringBuilder("redis-stack-server");
    cmd.append(" --appendonly yes");
    cmd.append(" --appendfsync everysec");
    cmd.append(" --dir /data");
    cmd.append(" --dbfilename dump.rdb");

    if (effectiveMode.isRecordMode()) {
      // Enable periodic saves in record mode
      cmd.append(" --save 60 1 --save 300 10");
    } else {
      // Disable saves in playback mode for speed
      cmd.append(" --save \"\"");
    }

    return cmd.toString();
  }

  private void waitForRedis() {
    for (int i = 0; i < 30; i++) {
      try {
        jedis.ping();
        long dbSize = jedis.dbSize();
        if (dbSize > 0) {
          System.out.println("VCR: Loaded " + dbSize + " keys from persisted data");
        }
        return;
      } catch (Exception e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for Redis", ie);
        }
      }
    }
    throw new RuntimeException("Timeout waiting for Redis to be ready");
  }

  /** Shuts down the VCR context and releases resources. */
  public void shutdown() {
    if (jedis != null) {
      jedis.close();
      jedis = null;
    }
    if (redisContainer != null) {
      redisContainer.stop();
      redisContainer = null;
    }
  }

  /** Resets call counters for a new test. */
  public void resetCallCounters() {
    callCounters.clear();
    currentCassetteKeys.clear();
  }

  /**
   * Sets the current test context.
   *
   * @param testId the unique test identifier
   */
  public void setCurrentTest(String testId) {
    this.currentTestId = testId;
  }

  /**
   * Gets the current test ID.
   *
   * @return the current test ID
   */
  public String getCurrentTestId() {
    return currentTestId;
  }

  /**
   * Generates a unique cassette key for the current test and call type.
   *
   * @param type the type of call (e.g., "llm", "embedding")
   * @return the generated cassette key
   */
  public String generateCassetteKey(String type) {
    String counterKey = currentTestId + ":" + type;
    int callIndex =
        callCounters.computeIfAbsent(counterKey, k -> new AtomicInteger()).incrementAndGet();

    String key = String.format("vcr:%s:%s:%04d", type, currentTestId, callIndex);
    currentCassetteKeys.add(key);
    return key;
  }

  /**
   * Gets the cassette keys generated for the current test.
   *
   * @return list of cassette keys
   */
  public List<String> getCurrentCassetteKeys() {
    return new ArrayList<>(currentCassetteKeys);
  }

  /**
   * Deletes the specified cassettes.
   *
   * @param keys the cassette keys to delete
   */
  public void deleteCassettes(List<String> keys) {
    if (jedis != null && keys != null && !keys.isEmpty()) {
      jedis.del(keys.toArray(new String[0]));
    }
  }

  /** Persists cassettes by triggering a Redis BGSAVE. */
  public void persistCassettes() {
    if (jedis == null) {
      return;
    }

    // Use a separate Jedis connection for BGSAVE since JedisPooled doesn't expose it directly
    String host = redisContainer.getHost();
    Integer port = redisContainer.getFirstMappedPort();
    try (Jedis directJedis = new Jedis(host, port)) {
      directJedis.bgsave();

      // Wait for save to complete
      long lastSave = directJedis.lastsave();
      for (int i = 0; i < 100; i++) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        if (directJedis.lastsave() != lastSave) {
          System.out.println("VCR: Persisted cassettes to disk");
          return;
        }
      }
      System.err.println("VCR: Warning - BGSAVE may not have completed");
    }
  }

  /**
   * Gets the VCR registry.
   *
   * @return the registry
   */
  public VCRRegistry getRegistry() {
    return registry;
  }

  /**
   * Gets the configured VCR mode.
   *
   * @return the configured mode
   */
  public VCRMode getConfiguredMode() {
    return config.mode();
  }

  /**
   * Gets the effective VCR mode for the current test.
   *
   * @return the effective mode
   */
  public VCRMode getEffectiveMode() {
    return effectiveMode;
  }

  /**
   * Sets the effective VCR mode.
   *
   * @param mode the mode to set
   */
  public void setEffectiveMode(VCRMode mode) {
    this.effectiveMode = mode;
  }

  /**
   * Gets the Redis client.
   *
   * @return the Jedis client
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Callers need direct access to shared Redis connection pool")
  public JedisPooled getJedis() {
    return jedis;
  }

  /**
   * Gets the data directory path.
   *
   * @return the data directory
   */
  public Path getDataDir() {
    return dataDir;
  }

  // Statistics methods

  /** Records a cache hit. */
  public void recordCacheHit() {
    cacheHits.incrementAndGet();
  }

  /** Records a cache miss. */
  public void recordCacheMiss() {
    cacheMisses.incrementAndGet();
  }

  /** Records an API call. */
  public void recordApiCall() {
    apiCalls.incrementAndGet();
  }

  /** Prints VCR statistics to stdout. */
  public void printStatistics() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    double hitRate = total > 0 ? (double) hits / total * 100 : 0;

    System.out.println("=== VCR Statistics ===");
    System.out.printf("Cache Hits: %d%n", hits);
    System.out.printf("Cache Misses: %d%n", misses);
    System.out.printf("API Calls: %d%n", apiCalls.get());
    System.out.printf("Hit Rate: %.1f%%%n", hitRate);
  }

  /**
   * Gets the cache hit count.
   *
   * @return number of cache hits
   */
  public long getCacheHits() {
    return cacheHits.get();
  }

  /**
   * Gets the cache miss count.
   *
   * @return number of cache misses
   */
  public long getCacheMisses() {
    return cacheMisses.get();
  }

  /**
   * Gets the API call count.
   *
   * @return number of API calls
   */
  public long getApiCalls() {
    return apiCalls.get();
  }
}
