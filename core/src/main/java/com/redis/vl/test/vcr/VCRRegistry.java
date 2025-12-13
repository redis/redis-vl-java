package com.redis.vl.test.vcr;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.JedisPooled;

/**
 * Tracks which tests have been recorded and their status.
 *
 * <p>The registry maintains metadata about each test in Redis, including:
 *
 * <ul>
 *   <li>Recording status (RECORDED, FAILED, MISSING)
 *   <li>Timestamp of last recording
 *   <li>Associated cassette keys
 *   <li>Error messages for failed tests
 * </ul>
 */
public class VCRRegistry {

  private static final String REGISTRY_KEY = "vcr:registry";
  private static final String TESTS_KEY = "vcr:registry:tests";

  private final JedisPooled jedis;
  private final Map<String, RecordingStatus> localCache = new ConcurrentHashMap<>();

  /** Recording status for a test. */
  public enum RecordingStatus {
    /** Test has been successfully recorded */
    RECORDED,
    /** Test recording failed */
    FAILED,
    /** Test has no recording */
    MISSING,
    /** Test recording is outdated */
    OUTDATED
  }

  /**
   * Creates a new VCR registry.
   *
   * @param jedis the Redis client
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "JedisPooled is intentionally shared for connection pooling")
  public VCRRegistry(JedisPooled jedis) {
    this.jedis = jedis;
  }

  /**
   * Registers a successful test recording.
   *
   * @param testId the unique test identifier
   * @param cassetteKeys the keys of cassettes recorded for this test
   */
  public void registerSuccess(String testId, List<String> cassetteKeys) {
    if (jedis == null) {
      localCache.put(testId, RecordingStatus.RECORDED);
      return;
    }

    String testKey = "vcr:test:" + testId;

    Map<String, String> data = new HashMap<>();
    data.put("status", RecordingStatus.RECORDED.name());
    data.put("recorded_at", Instant.now().toString());
    data.put("cassette_count", String.valueOf(cassetteKeys != null ? cassetteKeys.size() : 0));

    jedis.hset(testKey, data);
    jedis.sadd(TESTS_KEY, testId);

    // Store cassette keys
    if (cassetteKeys != null && !cassetteKeys.isEmpty()) {
      jedis.sadd(testKey + ":cassettes", cassetteKeys.toArray(new String[0]));
    }

    localCache.put(testId, RecordingStatus.RECORDED);
  }

  /**
   * Registers a failed test recording.
   *
   * @param testId the unique test identifier
   * @param error the error message
   */
  public void registerFailure(String testId, String error) {
    if (jedis == null) {
      localCache.put(testId, RecordingStatus.FAILED);
      return;
    }

    String testKey = "vcr:test:" + testId;

    Map<String, String> data = new HashMap<>();
    data.put("status", RecordingStatus.FAILED.name());
    data.put("recorded_at", Instant.now().toString());
    data.put("error", error != null ? error : "Unknown error");

    jedis.hset(testKey, data);
    jedis.sadd(TESTS_KEY, testId);

    localCache.put(testId, RecordingStatus.FAILED);
  }

  /**
   * Gets the recording status of a test.
   *
   * @param testId the unique test identifier
   * @return the recording status
   */
  public RecordingStatus getTestStatus(String testId) {
    // Check local cache first
    RecordingStatus cached = localCache.get(testId);
    if (cached != null) {
      return cached;
    }

    if (jedis == null) {
      return RecordingStatus.MISSING;
    }

    String testKey = "vcr:test:" + testId;
    String status = jedis.hget(testKey, "status");

    if (status == null) {
      return RecordingStatus.MISSING;
    }

    RecordingStatus result = RecordingStatus.valueOf(status);
    localCache.put(testId, result);
    return result;
  }

  /**
   * Determines the effective VCR mode for a test based on registry status.
   *
   * @param testId the unique test identifier
   * @param globalMode the global VCR mode
   * @return the effective mode to use for this test
   */
  public VCRMode determineEffectiveMode(String testId, VCRMode globalMode) {
    RecordingStatus status = getTestStatus(testId);

    return switch (globalMode) {
      case RECORD_NEW -> status == RecordingStatus.MISSING ? VCRMode.RECORD : VCRMode.PLAYBACK;

      case RECORD_FAILED ->
          status == RecordingStatus.FAILED || status == RecordingStatus.MISSING
              ? VCRMode.RECORD
              : VCRMode.PLAYBACK;

      case PLAYBACK_OR_RECORD ->
          status == RecordingStatus.RECORDED ? VCRMode.PLAYBACK : VCRMode.RECORD;

      default -> globalMode;
    };
  }

  /**
   * Gets all recorded test IDs.
   *
   * @return set of test IDs
   */
  public Set<String> getAllRecordedTests() {
    if (jedis == null) {
      return localCache.keySet();
    }
    return jedis.smembers(TESTS_KEY);
  }

  /**
   * Gets the cassette keys for a test.
   *
   * @param testId the unique test identifier
   * @return set of cassette keys
   */
  public Set<String> getCassetteKeys(String testId) {
    if (jedis == null) {
      return Set.of();
    }
    return jedis.smembers("vcr:test:" + testId + ":cassettes");
  }
}
