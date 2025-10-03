package com.redis.vl.utils.vectorize;

import com.redis.vl.extensions.cache.EmbeddingsCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LangChain4J-based vectorizer that can work with any LangChain4J EmbeddingModel. This provides a
 * unified interface to all embedding providers supported by LangChain4J including OpenAI, Azure
 * OpenAI, HuggingFace, Ollama, and local models.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Using OpenAI (API key from environment variable - recommended)
 * EmbeddingModel openAI = OpenAiEmbeddingModel.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .modelName("text-embedding-3-small")
 *     .build();
 * LangChain4JVectorizer vectorizer = new LangChain4JVectorizer(
 *     "text-embedding-3-small",
 *     openAI,
 *     1536
 * );
 *
 * // Using local model (no API key needed)
 * EmbeddingModel local = new AllMiniLmL6V2EmbeddingModel();
 * LangChain4JVectorizer vectorizer = new LangChain4JVectorizer("all-minilm-l6-v2", local);
 *
 * // With cache for better performance
 * EmbeddingsCache cache = new EmbeddingsCache("my-cache", redisClient);
 * vectorizer.setCache(cache);
 *
 * // Embed text
 * float[] embedding = vectorizer.embed("Hello world");
 * </pre>
 *
 * <p><strong>Security Best Practice:</strong> Always use environment variables or secure
 * configuration management for API keys. Never hardcode keys in your source code.
 */
public class LangChain4JVectorizer extends BaseVectorizer {

  private final EmbeddingModel embeddingModel;
  private volatile boolean dimensionsDetected = false;

  /**
   * Creates a new LangChain4JVectorizer with auto-detected dimensions.
   *
   * @param modelName The name of the embedding model
   * @param embeddingModel The LangChain4J embedding model instance
   */
  public LangChain4JVectorizer(String modelName, EmbeddingModel embeddingModel) {
    super(modelName, -1, "float32"); // Use -1 for auto-detect dimensions
    this.embeddingModel = embeddingModel;
  }

  /**
   * Creates a new LangChain4JVectorizer with known dimensions.
   *
   * @param modelName The name of the embedding model
   * @param embeddingModel The LangChain4J embedding model instance
   * @param dimensions The dimension of the embedding vectors
   */
  public LangChain4JVectorizer(String modelName, EmbeddingModel embeddingModel, int dimensions) {
    this(modelName, embeddingModel, dimensions, "float32");
  }

  /**
   * Creates a new LangChain4JVectorizer with known dimensions and data type.
   *
   * @param modelName The name of the embedding model
   * @param embeddingModel The LangChain4J embedding model instance
   * @param dimensions The dimension of the embedding vectors
   * @param dtype The data type for embeddings
   */
  public LangChain4JVectorizer(
      String modelName, EmbeddingModel embeddingModel, int dimensions, String dtype) {
    super(modelName, dimensions, dtype);
    this.embeddingModel = embeddingModel;
    this.dimensionsDetected = dimensions > 0;
  }

  /**
   * Creates a new LangChain4JVectorizer with cache.
   *
   * @param modelName The name of the embedding model
   * @param embeddingModel The LangChain4J embedding model instance
   * @param cache The embeddings cache to use
   */
  public LangChain4JVectorizer(
      String modelName, EmbeddingModel embeddingModel, EmbeddingsCache cache) {
    super(modelName, -1, "float32"); // Use -1 for auto-detect dimensions
    this.embeddingModel = embeddingModel;
    this.cache = Optional.ofNullable(cache);
  }

  @Override
  protected float[] generateEmbedding(String text) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Text cannot be null or empty");
    }

    try {
      Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
      float[] vector = embedding.vector();

      // Auto-detect dimensions on first call
      if (!dimensionsDetected && vector != null) {
        this.dimensions = vector.length;
        this.dimensionsDetected = true;
      }

      return vector;
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate embedding for text: " + text, e);
    }
  }

  @Override
  protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
    if (texts == null || texts.isEmpty()) {
      return new ArrayList<>();
    }

    try {
      // Process texts in batches to manage memory and API limits
      List<float[]> allEmbeddings = new ArrayList<>();

      for (int i = 0; i < texts.size(); i += batchSize) {
        int endIndex = Math.min(i + batchSize, texts.size());
        List<String> batch = texts.subList(i, endIndex);

        // Convert strings to TextSegments
        List<TextSegment> textSegments =
            batch.stream().map(TextSegment::from).collect(Collectors.toList());

        // Generate embeddings for this batch
        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();

        // Convert to float arrays
        List<float[]> batchVectors =
            embeddings.stream().map(Embedding::vector).collect(Collectors.toList());

        // Auto-detect dimensions on first batch
        if (!dimensionsDetected && !batchVectors.isEmpty() && batchVectors.get(0) != null) {
          this.dimensions = batchVectors.get(0).length;
          this.dimensionsDetected = true;
        }

        allEmbeddings.addAll(batchVectors);
      }

      return allEmbeddings;
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to generate embeddings for batch of " + texts.size() + " texts", e);
    }
  }

  /**
   * Get the underlying LangChain4J embedding model.
   *
   * @return The embedding model instance
   */
  public EmbeddingModel getEmbeddingModel() {
    return embeddingModel;
  }

  @Override
  public String getType() {
    return "langchain4j";
  }

  /**
   * Check if dimensions have been auto-detected.
   *
   * @return true if dimensions are known, false if still auto-detecting
   */
  public boolean areDimensionsDetected() {
    return dimensionsDetected;
  }

  /**
   * Manually set the dimensions (useful if you know them ahead of time).
   *
   * @param dimensions The number of dimensions
   */
  public void setDimensions(int dimensions) {
    if (dimensions > 0) {
      this.dimensions = dimensions;
      this.dimensionsDetected = true;
    }
  }
}
