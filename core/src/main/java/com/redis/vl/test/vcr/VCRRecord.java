package com.redis.vl.test.vcr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forces a specific test method to use RECORD mode, overriding the class-level VCR mode.
 *
 * <p>When applied to a test method, this annotation forces that specific test to always make real
 * API calls and record the responses, regardless of the class-level {@link VCRTest#mode()} setting.
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
 *     @VCRRecord
 *     void forcesRecording() {
 *         // Always makes real API calls and updates cache
 *     }
 * }
 * }</pre>
 *
 * @see VCRTest
 * @see VCRDisabled
 * @see VCRMode#RECORD
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VCRRecord {}
