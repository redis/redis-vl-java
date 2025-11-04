package com.redis.vl.utils;

/** Utility methods for RedisVL. */
public final class Utils {

  /**
   * Get current timestamp with microsecond precision.
   *
   * <p>Matches Python's current_timestamp() from redisvl.utils.utils which uses time.time()
   * returning seconds since epoch with microsecond precision (e.g., 1759898747.946505).
   *
   * @return Current time in seconds since epoch with microsecond precision as a double
   */
  public static double currentTimestamp() {
    // Python's time.time() returns seconds with microsecond precision
    // Java's System.currentTimeMillis() returns milliseconds
    // We need to convert and add nanosecond precision for uniqueness
    long millis = System.currentTimeMillis();
    long nanos = System.nanoTime() % 1_000_000; // Get sub-millisecond nanoseconds (0-999999)

    // Convert to seconds with microsecond precision
    double seconds = millis / 1000.0;
    double microseconds = nanos / 1_000_000.0; // Convert nanoseconds to fractional milliseconds

    return seconds + (microseconds / 1000.0); // Add microsecond component
  }

  /**
   * Normalize a Redis COSINE distance (0-2) to a similarity score (0-1).
   *
   * <p>Redis COSINE distance ranges from 0 (identical) to 2 (opposite). This method converts it to
   * a normalized similarity score where 0 is completely dissimilar and 1 is identical.
   *
   * <p>Matches Python's norm_cosine_distance() from redisvl.utils.utils.
   *
   * @param value Redis COSINE distance value (0-2)
   * @return Normalized similarity score (0-1)
   */
  public static float normCosineDistance(float value) {
    return Math.max((2.0f - value) / 2.0f, 0.0f);
  }

  /**
   * Denormalize a similarity score (0-1) to a Redis COSINE distance (0-2).
   *
   * <p>Converts a normalized similarity score (where 1 is identical and 0 is dissimilar) back to
   * Redis COSINE distance format (where 0 is identical and 2 is opposite).
   *
   * <p>Matches Python's denorm_cosine_distance() from redisvl.utils.utils.
   *
   * @param value Normalized similarity score (0-1)
   * @return Redis COSINE distance value (0-2)
   */
  public static float denormCosineDistance(float value) {
    return Math.max(2.0f - 2.0f * value, 0.0f);
  }

  private Utils() {
    // Prevent instantiation
  }
}
