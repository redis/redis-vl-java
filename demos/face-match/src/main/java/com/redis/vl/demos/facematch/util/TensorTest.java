package com.redis.vl.demos.facematch.util;

import ai.onnxruntime.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;

/** Test tensor creation and model inference. */
public class TensorTest {
  public static void main(String[] args) throws Exception {
    OrtEnvironment env = OrtEnvironment.getEnvironment();

    byte[] modelBytes = ModelDownloader.loadModelBytes();
    OrtSession session = env.createSession(modelBytes);

    // Create a simple test tensor with random data
    long[] shape = {1, 3, 112, 112};
    int totalSize = 1 * 3 * 112 * 112; // 37632

    FloatBuffer buffer = FloatBuffer.allocate(totalSize);

    // Fill with test data (zeros or small random values)
    for (int i = 0; i < totalSize; i++) {
      buffer.put(0.0f); // Start with zeros
    }
    buffer.flip();

    System.out.println("Creating tensor with shape: " + java.util.Arrays.toString(shape));
    System.out.println("Buffer capacity: " + buffer.capacity());
    System.out.println("Buffer remaining: " + buffer.remaining());

    try {
      OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
      System.out.println("✓ Tensor created successfully");

      System.out.println("Running inference...");
      var results = session.run(Collections.singletonMap("data", inputTensor));
      System.out.println("✓ Inference successful!");

      // Extract output
      OnnxTensor outputTensor = (OnnxTensor) results.get(0);
      float[][] output = (float[][]) outputTensor.getValue();
      System.out.println("Output shape: [" + output.length + ", " + output[0].length + "]");
      System.out.println("First 5 embedding values: ");
      for (int i = 0; i < Math.min(5, output[0].length); i++) {
        System.out.printf("  [%d]: %.6f%n", i, output[0][i]);
      }

      inputTensor.close();
      results.close();
    } catch (Exception e) {
      System.err.println("✗ Error: " + e.getMessage());
      e.printStackTrace();
    }

    session.close();
  }
}
