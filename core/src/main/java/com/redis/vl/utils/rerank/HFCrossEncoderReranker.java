package com.redis.vl.utils.rerank;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.redis.vl.utils.vectorize.HuggingFaceModelDownloader;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * HuggingFace Cross-Encoder reranker using real ONNX models.
 *
 * <p>This reranker downloads and runs actual cross-encoder models from HuggingFace. Cross-encoders
 * jointly encode query and document pairs to produce relevance scores, providing more accurate
 * ranking than bi-encoders (though slower).
 *
 * <p>Supported models: Any HuggingFace cross-encoder with ONNX export, such as:
 *
 * <ul>
 *   <li>cross-encoder/ms-marco-MiniLM-L-6-v2 (default)
 *   <li>cross-encoder/ms-marco-MiniLM-L-12-v2
 *   <li>cross-encoder/stsb-distilroberta-base
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * HFCrossEncoderReranker reranker = HFCrossEncoderReranker.builder()
 *     .model("cross-encoder/ms-marco-MiniLM-L-6-v2")
 *     .limit(5)
 *     .returnScore(true)
 *     .build();
 *
 * List<String> docs = Arrays.asList("doc1", "doc2", "doc3");
 * RerankResult result = reranker.rank("query", docs);
 * }</pre>
 */
@Slf4j
public class HFCrossEncoderReranker extends BaseReranker {

  private static final String DEFAULT_MODEL = "cross-encoder/ms-marco-MiniLM-L-6-v2";
  private static final int DEFAULT_LIMIT = 3;

  private final HuggingFaceModelDownloader downloader;
  private final CrossEncoderLoader modelLoader;
  private final String cacheDir;
  private OrtSession session;
  private OrtEnvironment environment;

  /**
   * Private constructor - use builder() to create instances.
   *
   * @param builder The builder instance
   */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Model initialization failure is properly handled with cleanup")
  private HFCrossEncoderReranker(Builder builder) {
    super(builder.model, null, builder.limit, builder.returnScore);

    this.cacheDir = builder.cacheDir;
    this.downloader = new HuggingFaceModelDownloader(cacheDir);
    this.modelLoader = new CrossEncoderLoader();

    initializeModel();
  }

  /** Default constructor with default model and settings. */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Model initialization failure is properly handled with cleanup")
  public HFCrossEncoderReranker() {
    this(builder());
  }

  /**
   * Create a new builder for HFCrossEncoderReranker.
   *
   * @return A new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  private static String getDefaultCacheDir() {
    return Paths.get(System.getProperty("user.home"), ".cache", "redisvl4j").toString();
  }

  private void initializeModel() {
    try {
      this.environment = OrtEnvironment.getEnvironment();

      log.info("Initializing HuggingFace cross-encoder model: {}", model);

      // Download model if not cached
      Path modelPath = downloader.downloadModel(model);

      // Load the ONNX cross-encoder model
      this.session = modelLoader.loadModel(modelPath, environment);

      log.info("Cross-encoder model {} initialized successfully", model);

    } catch (IOException | OrtException e) {
      cleanupResources();
      throw new RuntimeException("Failed to initialize HFCrossEncoderReranker: " + model, e);
    }
  }

  @Override
  public RerankResult rank(String query, List<?> docs) {
    validateQuery(query);
    validateDocs(docs);

    // Handle empty docs
    if (docs.isEmpty()) {
      return new RerankResult(
          Collections.emptyList(), returnScore ? Collections.emptyList() : null);
    }

    // Extract texts and original docs
    List<String> texts = new ArrayList<>();
    List<Object> validDocs = new ArrayList<>();

    for (Object doc : docs) {
      if (doc instanceof String) {
        texts.add((String) doc);
        validDocs.add(Map.of("content", doc));
      } else if (doc instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> docMap = (Map<String, Object>) doc;
        if (docMap.containsKey("content")) {
          texts.add(String.valueOf(docMap.get("content")));
          validDocs.add(doc);
        }
        // Skip docs without "content" field
      }
    }

    // If no valid docs, return empty result
    if (validDocs.isEmpty()) {
      return new RerankResult(
          Collections.emptyList(), returnScore ? Collections.emptyList() : null);
    }

    try {
      // Score all query-document pairs using real cross-encoder model
      List<Double> scores = scoreDocuments(query, texts);

      // Pair docs with scores and sort by score descending
      List<ScoredDocument> scoredDocs = new ArrayList<>();
      for (int i = 0; i < validDocs.size(); i++) {
        scoredDocs.add(new ScoredDocument(validDocs.get(i), scores.get(i)));
      }

      scoredDocs.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed());

      // Extract top K results
      int resultLimit = Math.min(limit, scoredDocs.size());
      List<Object> rerankedDocs =
          scoredDocs.stream()
              .limit(resultLimit)
              .map(ScoredDocument::getDocument)
              .collect(Collectors.toList());

      List<Double> rerankedScores =
          returnScore
              ? scoredDocs.stream()
                  .limit(resultLimit)
                  .map(ScoredDocument::getScore)
                  .collect(Collectors.toList())
              : null;

      // Convert Map<"content", String> back to String if original was String
      if (!docs.isEmpty() && docs.get(0) instanceof String) {
        rerankedDocs =
            rerankedDocs.stream()
                .map(
                    doc -> {
                      @SuppressWarnings("unchecked")
                      Map<String, Object> docMap = (Map<String, Object>) doc;
                      return docMap.get("content");
                    })
                .collect(Collectors.toList());
      }

      return new RerankResult(rerankedDocs, rerankedScores);

    } catch (OrtException e) {
      throw new RuntimeException("Failed to rerank documents with model: " + model, e);
    }
  }

  /**
   * Score each query-document pair using the real cross-encoder model.
   *
   * @param query The query string
   * @param texts The document texts
   * @return List of relevance scores from the model
   * @throws OrtException if model inference fails
   */
  private List<Double> scoreDocuments(String query, List<String> texts) throws OrtException {
    List<Double> scores = new ArrayList<>();

    for (String text : texts) {
      // Tokenize the query-document pair
      Map<String, long[][]> tokens = modelLoader.tokenizePair(query, text);

      // Run inference to get relevance score
      float score =
          modelLoader.runInference(
              session,
              tokens.get("input_ids"),
              tokens.get("token_type_ids"),
              tokens.get("attention_mask"));

      scores.add((double) score);
    }

    return scores;
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

  /** Close the reranker and clean up resources. */
  public void close() {
    cleanupResources();
  }

  /**
   * Builder for creating HFCrossEncoderReranker instances.
   *
   * <p>Provides a fluent API for configuring cross-encoder rerankers with model selection, result
   * limits, score inclusion, and cache directory settings.
   */
  public static class Builder {
    private String model = DEFAULT_MODEL;
    private int limit = DEFAULT_LIMIT;
    private boolean returnScore = true;
    private String cacheDir = getDefaultCacheDir();

    /** Creates a new builder with default settings. */
    public Builder() {
      // Default constructor with default settings already initialized above
    }

    /**
     * Set the model name.
     *
     * @param model HuggingFace model name (e.g., "cross-encoder/ms-marco-MiniLM-L-6-v2")
     * @return This builder
     */
    public Builder model(String model) {
      this.model = model;
      return this;
    }

    /**
     * Set the maximum number of results to return.
     *
     * @param limit Maximum number of results (must be positive)
     * @return This builder
     * @throws IllegalArgumentException if limit is not positive
     */
    public Builder limit(int limit) {
      if (limit <= 0) {
        throw new IllegalArgumentException("Limit must be a positive integer, got: " + limit);
      }
      this.limit = limit;
      return this;
    }

    /**
     * Set whether to return scores with results.
     *
     * @param returnScore true to include scores
     * @return This builder
     */
    public Builder returnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    /**
     * Set the cache directory for model storage.
     *
     * @param cacheDir Path to cache directory
     * @return This builder
     */
    public Builder cacheDir(String cacheDir) {
      this.cacheDir = cacheDir;
      return this;
    }

    /**
     * Build the HFCrossEncoderReranker instance.
     *
     * @return Configured reranker
     */
    public HFCrossEncoderReranker build() {
      return new HFCrossEncoderReranker(this);
    }
  }

  /** Helper class to pair documents with their scores. */
  private static class ScoredDocument {
    private final Object document;
    private final double score;

    ScoredDocument(Object document, double score) {
      this.document = document;
      this.score = score;
    }

    Object getDocument() {
      return document;
    }

    double getScore() {
      return score;
    }
  }
}
