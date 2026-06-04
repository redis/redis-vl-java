package com.redis.vl.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArrayUtilsTest {

  @Test
  void floatArrayToBytesAndBackRoundTrips() {
    float[] original = {1.0f, 2.5f, -3.14f, 0.0f};
    byte[] bytes = ArrayUtils.floatArrayToBytes(original);
    float[] result = ArrayUtils.bytesToFloatArray(bytes);
    assertThat(result).hasSize(original.length);
    for (int i = 0; i < original.length; i++) {
      assertThat(result[i]).isCloseTo(original[i], org.assertj.core.data.Offset.offset(0.001f));
    }
  }

  @Test
  void floatArrayToBytesNullReturnsNull() {
    assertThat(ArrayUtils.floatArrayToBytes(null)).isNull();
  }

  @Test
  void bytesToFloatArrayNullReturnsNull() {
    assertThat(ArrayUtils.bytesToFloatArray(null)).isNull();
  }

  @Test
  void floatArrayToBytesProducesCorrectLength() {
    float[] floats = {1.0f, 2.0f, 3.0f};
    byte[] bytes = ArrayUtils.floatArrayToBytes(floats);
    assertThat(bytes).hasSize(floats.length * Float.BYTES);
  }

  @Test
  void doubleArrayToFloatsConvertsCorrectly() {
    double[] doubles = {1.0, 2.5, -3.0};
    float[] floats = ArrayUtils.doubleArrayToFloats(doubles);
    assertThat(floats).hasSize(3);
    assertThat(floats[0]).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f));
    assertThat(floats[1]).isCloseTo(2.5f, org.assertj.core.data.Offset.offset(0.001f));
    assertThat(floats[2]).isCloseTo(-3.0f, org.assertj.core.data.Offset.offset(0.001f));
  }

  @Test
  void doubleArrayToFloatsNullReturnsNull() {
    assertThat(ArrayUtils.doubleArrayToFloats(null)).isNull();
  }

  @Test
  void emptyFloatArrayRoundTrips() {
    float[] empty = {};
    byte[] bytes = ArrayUtils.floatArrayToBytes(empty);
    float[] result = ArrayUtils.bytesToFloatArray(bytes);
    assertThat(result).isEmpty();
  }
}
