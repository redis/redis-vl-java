package com.redis.vl.utils.vectorize;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Vectorizer that uses Sentence Transformers models downloaded from HuggingFace. Models are
 * downloaded and cached locally, then run using ONNX Runtime. This provides the same functionality
 * as Python's sentence-transformers library.
 */
@Slf4j
public class SentenceTransformersVectorizer extends BaseVectorizer {

  private final HuggingFaceModelDownloader downloader;
  private final OnnxModelLoader modelLoader;
  private OrtSession session;
  private OrtEnvironment environment;
  private final String cacheDir;

  /** Create a vectorizer with default cache directory. */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Cleanup is handled properly before throwing exception")
  public SentenceTransformersVectorizer(String modelName) {
    super(modelName, -1, "FLOAT32"); // Dimensions will be set after loading model
    this.cacheDir = getDefaultCacheDir();
    this.downloader = new HuggingFaceModelDownloader(cacheDir);
    this.modelLoader = new OnnxModelLoader();

    initializeModelSafely();
  }

  /** Create a vectorizer with custom cache directory. */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Cleanup is handled properly before throwing exception")
  public SentenceTransformersVectorizer(String modelName, String cacheDir) {
    super(modelName, -1, "FLOAT32"); // Dimensions will be set after loading model
    this.cacheDir = cacheDir;
    this.downloader = new HuggingFaceModelDownloader(cacheDir);
    this.modelLoader = new OnnxModelLoader();

    initializeModelSafely();
  }

  private void initializeModelSafely() {
    try {
      this.environment = OrtEnvironment.getEnvironment();
      initializeModel();
    } catch (Exception e) {
      // Clean up resources before throwing exception
      cleanupResources();
      throw new RuntimeException("Failed to initialize SentenceTransformersVectorizer", e);
    }
  }

  private void initializeModel() throws IOException, OrtException {
    log.info("Initializing SentenceTransformers model: {}", modelName);

    // Download model if not cached
    Path modelPath = downloader.downloadModel(modelName);

    // Load the ONNX model with the environment
    this.session = modelLoader.loadModel(modelPath, environment);

    // Update dimensions from loaded model
    this.dimensions = modelLoader.getEmbeddingDimension();

    log.info("Model {} initialized with {} dimensions", modelName, dimensions);
  }

  @Override
  protected float[] generateEmbedding(String text) {
    try {
      // Tokenize the text
      long[][] tokenIds = modelLoader.tokenize(text);

      // Create input tensor
      OnnxTensor inputTensor = OnnxTensor.createTensor(environment, tokenIds);

      try {
        // Run inference
        float[][] embeddings = modelLoader.runInference(session, inputTensor);

        // Return the first embedding
        return embeddings[0];

      } finally {
        inputTensor.close();
      }

    } catch (OrtException e) {
      throw new RuntimeException("Failed to generate embedding for text: " + text, e);
    }
  }

  @Override
  protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
    List<float[]> allEmbeddings = new ArrayList<>();

    // Process texts in batches
    for (int i = 0; i < texts.size(); i += batchSize) {
      int end = Math.min(i + batchSize, texts.size());
      List<String> batch = texts.subList(i, end);

      try {
        // Tokenize batch
        long[][] tokenIds = modelLoader.tokenizeBatch(batch);

        // Create input tensor
        OnnxTensor inputTensor = OnnxTensor.createTensor(environment, tokenIds);

        try {
          // Run inference
          float[][] embeddings = modelLoader.runInference(session, inputTensor);

          // Add to results
          for (float[] embedding : embeddings) {
            allEmbeddings.add(embedding);
          }

        } finally {
          inputTensor.close();
        }

      } catch (OrtException e) {
        throw new RuntimeException("Failed to generate embeddings for batch", e);
      }
    }

    return allEmbeddings;
  }

  /**
   * Generate embeddings for a batch of texts with default batch size. Returns List of List of Float
   * for convenience.
   */
  public List<List<Float>> embedBatchAsLists(List<String> texts) {
    List<float[]> embeddings = generateEmbeddingsBatch(texts, 32);

    // Convert to List<List<Float>> for compatibility
    List<List<Float>> result = new ArrayList<>(embeddings.size());
    for (float[] embedding : embeddings) {
      result.add(floatArrayToList(embedding));
    }

    return result;
  }

  private List<Float> floatArrayToList(float[] array) {
    List<Float> list = new ArrayList<>(array.length);
    for (float value : array) {
      list.add(value);
    }
    return list;
  }

  private static String getDefaultCacheDir() {
    return Paths.get(System.getProperty("user.home"), ".cache", "redisvl4j").toString();
  }

  private void cleanupResources() {
    if (session != null) {
      try {
        session.close();
        session = null;
      } catch (OrtException e) {
        log.warn("Error closing ONNX session during cleanup", e);
      }
    }
  }

  public void close() {
    cleanupResources();
  }
}
