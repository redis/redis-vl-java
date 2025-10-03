package com.redis.vl.utils.rerank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HFCrossEncoderReranker.
 *
 * <p>These tests verify the API and basic behavior without requiring model downloads. Integration
 * tests that actually load models are in HFCrossEncoderRerankerIntegrationTest.
 */
class HFCrossEncoderRerankerTest {

  @Test
  void testBuilderDefaults() {
    // Builder should work without downloading
    HFCrossEncoderReranker.Builder builder = HFCrossEncoderReranker.builder();

    // Just test builder methods, don't call build() which would download models
    builder.model("cross-encoder/ms-marco-MiniLM-L-6-v2");
    builder.limit(5);
    builder.returnScore(false);

    // Verify builder validation
    assertThrows(IllegalArgumentException.class, () -> builder.limit(0));
    assertThrows(IllegalArgumentException.class, () -> builder.limit(-1));
  }

  @Test
  void testBuilderValidation() {
    HFCrossEncoderReranker.Builder builder = HFCrossEncoderReranker.builder();

    Exception exception = assertThrows(IllegalArgumentException.class, () -> builder.limit(0));
    assertTrue(exception.getMessage().contains("positive"));

    exception = assertThrows(IllegalArgumentException.class, () -> builder.limit(-1));
    assertTrue(exception.getMessage().contains("positive"));
  }

  @Test
  void testBuilderChaining() {
    HFCrossEncoderReranker.Builder builder =
        HFCrossEncoderReranker.builder()
            .model("custom-model")
            .limit(10)
            .returnScore(false)
            .cacheDir("/tmp/test-cache");

    // Builder should support method chaining
    assertNotNull(builder);
  }
}
