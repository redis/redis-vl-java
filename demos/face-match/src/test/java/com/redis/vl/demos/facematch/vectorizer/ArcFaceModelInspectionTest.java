package com.redis.vl.demos.facematch.vectorizer;

import static org.junit.jupiter.api.Assertions.*;

import ai.onnxruntime.*;
import com.redis.vl.demos.facematch.util.ModelDownloader;
import java.util.Map;
import javafx.embed.swing.JFXPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Test to inspect ArcFace ONNX model input/output specifications. */
public class ArcFaceModelInspectionTest {

  @BeforeAll
  static void initJavaFX() {
    new JFXPanel(); // Initialize JavaFX toolkit
  }

  @Test
  void testInspectModelInputOutput() throws Exception {
    OrtEnvironment env = OrtEnvironment.getEnvironment();

    byte[] modelBytes = ModelDownloader.loadModelBytes();
    assertNotNull(modelBytes, "ArcFace model bytes should not be null");

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
