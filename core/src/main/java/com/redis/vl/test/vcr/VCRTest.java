package com.redis.vl.test.vcr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enables VCR (Video Cassette Recorder) functionality for a test class.
 *
 * <p>When applied to a test class, this annotation enables automatic recording and playback of LLM
 * API calls. This allows tests to run without making actual API calls after initial recording,
 * reducing costs and providing deterministic test execution.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @VCRTest(mode = VCRMode.PLAYBACK)
 * class MyLLMIntegrationTest {
 *
 *     @Test
 *     void testLLMResponse() {
 *         // LLM calls are automatically intercepted
 *         String response = chatModel.generate("Hello, world!");
 *         assertThat(response).isNotEmpty();
 *     }
 * }
 * }</pre>
 *
 * <p>Configuration options:
 *
 * <ul>
 *   <li>{@link #mode()} - The VCR operating mode (default: PLAYBACK)
 *   <li>{@link #dataDir()} - Directory for storing cassettes (default: src/test/resources/vcr-data)
 *   <li>{@link #redisImage()} - Docker image for Redis container (default:
 *       redis/redis-stack:latest)
 * </ul>
 *
 * @see VCRMode
 * @see VCRRecord
 * @see VCRDisabled
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(VCRExtension.class)
public @interface VCRTest {

  /**
   * The VCR operating mode for this test class.
   *
   * @return the VCR mode to use (default: PLAYBACK)
   */
  VCRMode mode() default VCRMode.PLAYBACK;

  /**
   * The directory where VCR cassettes (recorded responses) are stored.
   *
   * <p>This directory will contain:
   *
   * <ul>
   *   <li>dump.rdb - Redis RDB snapshot
   *   <li>appendonlydir/ - Redis AOF segments
   * </ul>
   *
   * <p>The directory is relative to the project root unless an absolute path is provided.
   *
   * @return the data directory path (default: src/test/resources/vcr-data)
   */
  String dataDir() default "src/test/resources/vcr-data";

  /**
   * The Docker image to use for the Redis container.
   *
   * <p>The image should be a Redis Stack image that includes RediSearch and RedisJSON modules for
   * optimal functionality.
   *
   * @return the Redis Docker image name (default: redis/redis-stack:latest)
   */
  String redisImage() default "redis/redis-stack:latest";
}
