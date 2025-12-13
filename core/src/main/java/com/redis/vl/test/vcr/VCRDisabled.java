package com.redis.vl.test.vcr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disables VCR functionality for a specific test method.
 *
 * <p>When applied to a test method, this annotation completely disables VCR for that test. All LLM
 * calls will go to real APIs and nothing will be recorded or played back.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @VCRTest(mode = VCRMode.PLAYBACK)
 * class MyLLMIntegrationTest {
 *
 *     @Test
 *     void usesPlayback() {
 *         // Uses cached responses
 *     }
 *
 *     @Test
 *     @VCRDisabled
 *     void bypassesVCR() {
 *         // VCR is completely disabled - real API calls, no caching
 *     }
 * }
 * }</pre>
 *
 * @see VCRTest
 * @see VCRRecord
 * @see VCRMode#OFF
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VCRDisabled {}
