package com.redis.vl.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Utility class for array conversions. */
public class ArrayUtils {

  private ArrayUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Convert float array to byte array using little-endian byte order.
   *
   * @param floats The float array to convert
   * @return The byte array representation
   */
  public static byte[] floatArrayToBytes(float[] floats) {
    if (floats == null) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    for (float f : floats) {
      buffer.putFloat(f);
    }
    return buffer.array();
  }

  /**
   * Convert byte array to float array using little-endian byte order.
   *
   * @param bytes The byte array to convert
   * @return The float array representation
   */
  public static float[] bytesToFloatArray(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    float[] floats = new float[bytes.length / Float.BYTES];
    for (int i = 0; i < floats.length; i++) {
      floats[i] = buffer.getFloat();
    }
    return floats;
  }

  /**
   * Convert double array to float array.
   *
   * @param doubles The double array to convert
   * @return The float array representation
   */
  public static float[] doubleArrayToFloats(double[] doubles) {
    if (doubles == null) {
      return null;
    }
    float[] floats = new float[doubles.length];
    for (int i = 0; i < doubles.length; i++) {
      floats[i] = (float) doubles[i];
    }
    return floats;
  }
}
