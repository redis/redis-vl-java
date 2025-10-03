package com.redis.vl.utils.rerank;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for document rerankers.
 *
 * <p>Rerankers improve search result quality by reordering documents based on their relevance to a
 * query using specialized models (cross-encoders, API-based rerankers, etc.).
 *
 * <p>Implementations must provide the {@link #rank(String, List)} method which takes a query and a
 * list of documents and returns a {@link RerankResult} with the documents reordered by relevance.
 */
public abstract class BaseReranker {

  /** The model name or identifier used for reranking. */
  protected final String model;

  /** Optional list of field names to rank by (may be null). */
  protected final List<String> rankBy;

  /** Maximum number of results to return. */
  protected final int limit;

  /** Whether to include relevance scores in results. */
  protected final boolean returnScore;

  /**
   * Create a reranker with the specified configuration.
   *
   * @param model The model name or identifier
   * @param rankBy Optional list of field names to rank by (may be null)
   * @param limit Maximum number of results to return (must be positive)
   * @param returnScore Whether to include relevance scores in results
   * @throws IllegalArgumentException if limit is not positive
   */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Validation exceptions in constructor are intentional and safe")
  protected BaseReranker(String model, List<String> rankBy, int limit, boolean returnScore) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be a positive integer, got: " + limit);
    }

    this.model = model;
    this.rankBy = rankBy != null ? Collections.unmodifiableList(new ArrayList<>(rankBy)) : null;
    this.limit = limit;
    this.returnScore = returnScore;
  }

  /**
   * Rerank documents based on their relevance to the query.
   *
   * @param query The search query
   * @param docs The documents to rerank (either List&lt;String&gt; or List&lt;Map&lt;String,
   *     Object&gt;&gt;)
   * @return RerankResult containing reranked documents and optional scores
   * @throws IllegalArgumentException if query or docs are invalid
   */
  public abstract RerankResult rank(String query, List<?> docs);

  /**
   * Get the model name.
   *
   * @return Model identifier
   */
  public String getModel() {
    return model;
  }

  /**
   * Get the fields to rank by.
   *
   * @return Unmodifiable list of field names, or null if not applicable
   */
  public List<String> getRankBy() {
    return rankBy; // Already unmodifiable from constructor
  }

  /**
   * Get the maximum number of results to return.
   *
   * @return Result limit
   */
  public int getLimit() {
    return limit;
  }

  /**
   * Check if scores should be returned with results.
   *
   * @return true if scores are included
   */
  public boolean isReturnScore() {
    return returnScore;
  }

  /**
   * Validate query parameter.
   *
   * @param query The query string to validate
   * @throws IllegalArgumentException if query is null or empty
   */
  protected void validateQuery(String query) {
    if (query == null) {
      throw new IllegalArgumentException("query cannot be null");
    }
    if (query.trim().isEmpty()) {
      throw new IllegalArgumentException("query cannot be empty");
    }
  }

  /**
   * Validate documents parameter.
   *
   * @param docs The documents list to validate
   * @throws IllegalArgumentException if docs is null
   */
  protected void validateDocs(List<?> docs) {
    if (docs == null) {
      throw new IllegalArgumentException("docs cannot be null");
    }
  }
}
