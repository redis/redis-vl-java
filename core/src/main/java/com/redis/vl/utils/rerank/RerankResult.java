package com.redis.vl.utils.rerank;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result from a reranking operation containing reranked documents and optional scores.
 *
 * <p>This class encapsulates the output of a reranker, which includes the reordered documents and
 * their relevance scores (if requested).
 */
public class RerankResult {

  private final List<?> documents;
  private final List<Double> scores;

  /**
   * Create a rerank result with documents and scores.
   *
   * @param documents The reranked documents (either List&lt;String&gt; or List&lt;Map&lt;String,
   *     Object&gt;&gt;)
   * @param scores The relevance scores for each document, or null if scores were not requested
   * @throws IllegalArgumentException if documents is null, or if scores is non-null but has a
   *     different size than documents
   */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Validation exceptions in constructor are intentional and safe")
  public RerankResult(List<?> documents, List<Double> scores) {
    if (documents == null) {
      throw new IllegalArgumentException("documents cannot be null");
    }

    if (scores != null && documents.size() != scores.size()) {
      throw new IllegalArgumentException(
          "documents and scores must have the same size. Documents: "
              + documents.size()
              + ", Scores: "
              + scores.size());
    }

    // Make defensive copies to ensure immutability
    this.documents = Collections.unmodifiableList(new ArrayList<>(documents));
    this.scores = scores != null ? Collections.unmodifiableList(new ArrayList<>(scores)) : null;
  }

  /**
   * Get the reranked documents.
   *
   * @return Immutable list of reranked documents
   */
  public List<?> getDocuments() {
    return documents;
  }

  /**
   * Get the relevance scores for each document.
   *
   * @return Immutable list of scores, or null if scores were not requested
   */
  public List<Double> getScores() {
    return scores;
  }

  /**
   * Check if this result includes scores.
   *
   * @return true if scores are included, false otherwise
   */
  public boolean hasScores() {
    return scores != null;
  }

  /**
   * Get the highest score from the result.
   *
   * @return The maximum score
   * @throws IllegalStateException if no scores are available or if the score list is empty
   */
  public double getTopScore() {
    if (scores == null) {
      throw new IllegalStateException("No scores available in this result");
    }
    if (scores.isEmpty()) {
      throw new IllegalStateException("Score list is empty");
    }
    return scores.get(0); // Scores are already sorted descending
  }

  @Override
  public String toString() {
    return "RerankResult{"
        + "documents="
        + documents.size()
        + " items"
        + ", scores="
        + (scores != null ? scores.size() + " scores" : "none")
        + '}';
  }
}
