package com.redis.vl.langchain4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4J ContentRetriever implementation using RedisVL as the backend.
 *
 * <p>This retriever enables RAG (Retrieval-Augmented Generation) workflows by retrieving relevant
 * content from Redis based on semantic similarity.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create components
 * EmbeddingStore<TextSegment> embeddingStore = new RedisVLEmbeddingStore(searchIndex);
 * EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
 *
 * // Create retriever
 * ContentRetriever retriever = RedisVLContentRetriever.builder()
 *     .embeddingStore(embeddingStore)
 *     .embeddingModel(embeddingModel)
 *     .maxResults(5)
 *     .minScore(0.7)
 *     .build();
 *
 * // Use in RAG chain
 * String response = ConversationalRetrievalChain.builder()
 *     .chatLanguageModel(chatModel)
 *     .contentRetriever(retriever)
 *     .build()
 *     .execute("What is attention mechanism?");
 * }</pre>
 */
public class RedisVLContentRetriever implements ContentRetriever {

  private final RedisVLEmbeddingStore embeddingStore;
  private final EmbeddingModel embeddingModel;
  private final int maxResults;
  private final double minScore;

  /**
   * Creates a new RedisVLContentRetriever.
   *
   * @param embeddingStore The Redis embedding store
   * @param embeddingModel The embedding model to encode queries
   */
  public RedisVLContentRetriever(
      RedisVLEmbeddingStore embeddingStore, EmbeddingModel embeddingModel) {
    this(embeddingStore, embeddingModel, 3, 0.7);
  }

  /**
   * Creates a new RedisVLContentRetriever with custom parameters.
   *
   * @param embeddingStore The Redis embedding store
   * @param embeddingModel The embedding model to encode queries
   * @param maxResults Maximum number of results to return
   * @param minScore Minimum similarity score (0-1)
   */
  public RedisVLContentRetriever(
      RedisVLEmbeddingStore embeddingStore,
      EmbeddingModel embeddingModel,
      int maxResults,
      double minScore) {
    this.embeddingStore = embeddingStore;
    this.embeddingModel = embeddingModel;
    this.maxResults = maxResults;
    this.minScore = minScore;
  }

  @Override
  public List<Content> retrieve(Query query) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }

    // 1. Embed the query
    String queryText = query.text();
    Embedding queryEmbedding = embeddingModel.embed(queryText).content();

    // 2. Build search request
    dev.langchain4j.store.embedding.EmbeddingSearchRequest request =
        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(minScore)
            .build();

    // 3. Search for relevant documents using new search() method
    dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> searchResult =
        embeddingStore.search(request);

    // 4. Convert to Content objects
    return searchResult.matches().stream()
        .map(
            match -> {
              TextSegment segment = match.embedded();
              if (segment == null) {
                return null;
              }
              return Content.from(segment);
            })
        .filter(content -> content != null)
        .collect(Collectors.toList());
  }

  /**
   * Creates a builder for RedisVLContentRetriever.
   *
   * @return A new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for RedisVLContentRetriever. */
  public static class Builder {
    private RedisVLEmbeddingStore embeddingStore;
    private EmbeddingModel embeddingModel;
    private int maxResults = 3;
    private double minScore = 0.7;

    /**
     * Sets the embedding store.
     *
     * @param embeddingStore The Redis embedding store
     * @return This builder
     */
    public Builder embeddingStore(RedisVLEmbeddingStore embeddingStore) {
      this.embeddingStore = embeddingStore;
      return this;
    }

    /**
     * Sets the embedding model.
     *
     * @param embeddingModel The embedding model
     * @return This builder
     */
    public Builder embeddingModel(EmbeddingModel embeddingModel) {
      this.embeddingModel = embeddingModel;
      return this;
    }

    /**
     * Sets the maximum number of results.
     *
     * @param maxResults Maximum results
     * @return This builder
     */
    public Builder maxResults(int maxResults) {
      this.maxResults = maxResults;
      return this;
    }

    /**
     * Sets the minimum similarity score.
     *
     * @param minScore Minimum score (0-1)
     * @return This builder
     */
    public Builder minScore(double minScore) {
      this.minScore = minScore;
      return this;
    }

    /**
     * Builds the retriever.
     *
     * @return A new RedisVLContentRetriever
     */
    public RedisVLContentRetriever build() {
      if (embeddingStore == null) {
        throw new IllegalArgumentException("EmbeddingStore is required");
      }
      if (embeddingModel == null) {
        throw new IllegalArgumentException("EmbeddingModel is required");
      }
      return new RedisVLContentRetriever(embeddingStore, embeddingModel, maxResults, minScore);
    }
  }
}
