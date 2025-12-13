package com.redis.vl.test.vcr;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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
  private VCRCassetteStore cassetteStore;

  private String currentTestId;
  private VCRMode effectiveMode;
  private final List<String> currentCassetteKeys = new ArrayList<>();
  private final Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

  // Statistics
  private final AtomicLong cacheHits = new AtomicLong();
  private final AtomicLong cacheMisses = new AtomicLong();
  private final AtomicLong apiCalls = new AtomicLong();

  /** Environment variable name for overriding VCR mode. */
  public static final String VCR_MODE_ENV = "VCR_MODE";

  /**
   * Creates a new VCR context with the given configuration.
   *
   * <p>The VCR mode can be overridden via the {@code VCR_MODE} environment variable. Valid values
   * are: PLAYBACK, PLAYBACK_OR_RECORD, RECORD, OFF. If the environment variable is set, it takes
   * precedence over the annotation's mode setting.
   *
   * @param config the VCR test configuration
   */
  public VCRContext(VCRTest config) {
    this.config = config;
    this.dataDir = Path.of(config.dataDir());
    this.effectiveMode = resolveMode(config.mode());
  }

  /**
   * Resolves the effective VCR mode, checking the environment variable first.
   *
   * @param annotationMode the mode specified in the annotation
   * @return the effective mode (env var takes precedence)
   */
  private static VCRMode resolveMode(VCRMode annotationMode) {
    String envMode = System.getenv(VCR_MODE_ENV);
    if (envMode != null && !envMode.isEmpty()) {
      try {
        VCRMode mode = VCRMode.valueOf(envMode.toUpperCase());
        System.out.println(
            "VCR: Using mode from "
                + VCR_MODE_ENV
                + " environment variable: "
                + mode
                + " (annotation was: "
                + annotationMode
                + ")");
        return mode;
      } catch (IllegalArgumentException e) {
        System.err.println(
            "VCR: Invalid "
                + VCR_MODE_ENV
                + " value '"
                + envMode
                + "'. Valid values: PLAYBACK, PLAYBACK_OR_RECORD, RECORD, OFF. Using annotation value: "
                + annotationMode);
      }
    }
    return annotationMode;
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

    // Initialize registry and cassette store
    registry = new VCRRegistry(jedis);
    cassetteStore = new VCRCassetteStore(jedis);
  }

  /**
   * Gets the cassette store for storing/retrieving cassettes.
   *
   * @return the cassette store
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Callers need direct access to shared cassette store")
  public VCRCassetteStore getCassetteStore() {
    return cassetteStore;
  }

  /** Starts the Redis container with appropriate persistence configuration. */
  @SuppressWarnings("resource")
  private void startRedis() {
    String redisCommand = buildRedisCommand();

    // In playback mode, copy data to temp directory to prevent modifications to source files
    Path mountPath = dataDir;
    if (!effectiveMode.isRecordMode()) {
      try {
        mountPath = copyDataToTemp();
      } catch (Exception e) {
        System.err.println(
            "VCR: Failed to copy data to temp directory, using original: " + e.getMessage());
        mountPath = dataDir;
      }
    }

    redisContainer =
        new GenericContainer<>(DockerImageName.parse(config.redisImage()))
            .withExposedPorts(6379)
            .withFileSystemBind(mountPath.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE)
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
    cmd.append(" --dir /data");
    cmd.append(" --dbfilename dump.rdb");

    if (effectiveMode.isRecordMode()) {
      // Enable AOF and periodic saves in record mode
      cmd.append(" --appendonly yes");
      cmd.append(" --appendfsync everysec");
      cmd.append(" --save 60 1 --save 300 10");
    } else {
      // Disable all persistence in playback mode (read-only)
      cmd.append(" --appendonly no");
      cmd.append(" --save \"\"");
    }

    return cmd.toString();
  }

  /**
   * Copies VCR data to a temporary directory to prevent modifications to source files. Used in
   * playback mode to ensure cassette files are not modified.
   *
   * @return path to the temporary directory containing the copied data
   * @throws IOException if copying fails
   */
  private Path copyDataToTemp() throws IOException {
    Path tempDir = Files.createTempDirectory("vcr-playback-");
    tempDir.toFile().deleteOnExit();

    Files.walkFileTree(
        dataDir,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            Path targetDir = tempDir.resolve(dataDir.relativize(dir));
            Files.createDirectories(targetDir);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Path targetFile = tempDir.resolve(dataDir.relativize(file));
            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return FileVisitResult.CONTINUE;
          }
        });

    System.out.println(
        "VCR: Using temporary copy at " + tempDir + " for playback (read-only protection)");
    return tempDir;
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
