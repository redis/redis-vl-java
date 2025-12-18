package com.redis.vl.extensions.summarization;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

/** OpenNLP-based sentence splitting utility. Thread-safe after initialization. */
public class SentenceSplitter {

  private final SentenceDetectorME detector;

  /**
   * Create a sentence splitter using the default English model. The model is loaded from the
   * classpath.
   */
  public SentenceSplitter() {
    this(loadDefaultModel());
  }

  /**
   * Create a sentence splitter with a custom model.
   *
   * @param model The OpenNLP sentence model to use
   */
  public SentenceSplitter(SentenceModel model) {
    this.detector = new SentenceDetectorME(model);
  }

  private static SentenceModel loadDefaultModel() {
    try (InputStream modelIn =
        SentenceSplitter.class.getResourceAsStream("/models/opennlp/en-sent.bin")) {
      if (modelIn == null) {
        throw new IllegalStateException(
            "OpenNLP English sentence model not found. "
                + "Ensure 'en-sent.bin' is in resources/models/opennlp/");
      }
      return new SentenceModel(modelIn);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load OpenNLP sentence model", e);
    }
  }

  /**
   * Split text into sentences.
   *
   * @param text The text to split
   * @return List of sentences
   */
  public List<String> split(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    synchronized (detector) {
      return Arrays.asList(detector.sentDetect(text));
    }
  }

  /**
   * Split text into sentences with position spans.
   *
   * @param text The text to split
   * @return Array of Span objects with start/end positions
   */
  public opennlp.tools.util.Span[] splitWithSpans(String text) {
    if (text == null || text.isBlank()) {
      return new opennlp.tools.util.Span[0];
    }
    synchronized (detector) {
      return detector.sentPosDetect(text);
    }
  }
}
