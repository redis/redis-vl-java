package com.redis.vl.demos.facematch.util;

import ai.onnxruntime.*;
import java.io.IOException;
import java.util.Map;

/** Utility to inspect ONNX model input/output specifications. */
public class ModelInspector {
  public static void main(String[] args) throws Exception {
    OrtEnvironment env = OrtEnvironment.getEnvironment();

    byte[] modelBytes = ModelDownloader.loadModelBytes();
    OrtSession session = env.createSession(modelBytes);

    System.out.println("=== ArcFace Model Inspection ===\n");

    // Inspect inputs
    System.out.println("INPUTS:");
    Map<String, NodeInfo> inputs = session.getInputInfo();
    for (Map.Entry<String, NodeInfo> entry : inputs.entrySet()) {
      String name = entry.getKey();
      NodeInfo info = entry.getValue();

      System.out.println("  Name: " + name);
      System.out.println("  Info: " + info.getInfo());

      if (info.getInfo() instanceof TensorInfo) {
        TensorInfo tensorInfo = (TensorInfo) info.getInfo();
        System.out.println("  Type: " + tensorInfo.type);
        System.out.println("  Shape: " + java.util.Arrays.toString(tensorInfo.getShape()));
      }
      System.out.println();
    }

    // Inspect outputs
    System.out.println("OUTPUTS:");
    Map<String, NodeInfo> outputs = session.getOutputInfo();
    for (Map.Entry<String, NodeInfo> entry : outputs.entrySet()) {
      String name = entry.getKey();
      NodeInfo info = entry.getValue();

      System.out.println("  Name: " + name);
      System.out.println("  Info: " + info.getInfo());

      if (info.getInfo() instanceof TensorInfo) {
        TensorInfo tensorInfo = (TensorInfo) info.getInfo();
        System.out.println("  Type: " + tensorInfo.type);
        System.out.println("  Shape: " + java.util.Arrays.toString(tensorInfo.getShape()));
      }
      System.out.println();
    }

    session.close();

    System.out.println("=== Model Inspection Complete ===");
  }
}
