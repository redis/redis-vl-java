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

  private Utils() {
    // Prevent instantiation
  }
}
