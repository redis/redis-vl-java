package com.redis.vl.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilsTest {

  @Test
  void currentTimestampReturnsPositiveValue() {
    assertThat(Utils.currentTimestamp()).isPositive();
  }

  @Test
  void currentTimestampIsCloseToCurrentTimeInSeconds() {
    double now = System.currentTimeMillis() / 1000.0;
    double ts = Utils.currentTimestamp();
    assertThat(ts).isBetween(now - 1.0, now + 1.0);
  }

  @Test
  void normCosineDistanceOfZeroIsOne() {
    // distance 0 = identical vectors → similarity 1.0
    assertThat(Utils.normCosineDistance(0.0f)).isEqualTo(1.0f);
  }

  @Test
  void normCosineDistanceOfTwoIsZero() {
    // distance 2 = opposite vectors → similarity 0.0
    assertThat(Utils.normCosineDistance(2.0f)).isEqualTo(0.0f);
  }

  @Test
  void normCosineDistanceOfOneIsHalf() {
    assertThat(Utils.normCosineDistance(1.0f))
        .isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
  }

  @Test
  void normCosineDistanceClampsNegativeToZero() {
    // values > 2 would produce negative similarity — should clamp to 0
    assertThat(Utils.normCosineDistance(3.0f)).isEqualTo(0.0f);
  }

  @Test
  void denormCosineDistanceOfOneIsZero() {
    // similarity 1.0 = identical → distance 0
    assertThat(Utils.denormCosineDistance(1.0f)).isEqualTo(0.0f);
  }

  @Test
  void denormCosineDistanceOfZeroIsTwo() {
    // similarity 0.0 = dissimilar → distance 2
    assertThat(Utils.denormCosineDistance(0.0f)).isEqualTo(2.0f);
  }

  @Test
  void denormCosineDistanceOfHalfIsOne() {
    assertThat(Utils.denormCosineDistance(0.5f))
        .isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f));
  }

  @Test
  void normAndDenormAreInverses() {
    float original = 1.3f;
    float roundTripped = Utils.denormCosineDistance(Utils.normCosineDistance(original));
    assertThat(roundTripped).isCloseTo(original, org.assertj.core.data.Offset.offset(0.001f));
  }
}
