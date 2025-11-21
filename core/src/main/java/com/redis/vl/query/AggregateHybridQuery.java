package com.redis.vl.query;

import java.util.List;
import java.util.Set;

/**
 * AggregateHybridQuery combines text and vector search in Redis using aggregation.
 *
 * <p>This is the primary name for hybrid queries in RedisVL, matching the Python implementation.
 * It extends {@link HybridQuery} which contains the full implementation.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:57-315 (AggregateHybridQuery class)
 *
 * <p>It allows you to perform a hybrid search using both text and vector similarity. It scores
 * documents based on a weighted combination of text and vector similarity.
 *
 * <p>Python equivalent:
 *
 * <pre>
 * query = AggregateHybridQuery(
 *     text="example text",
 *     text_field_name="text_field",
 *     vector=[0.1, 0.2, 0.3],
 *     vector_field_name="vector_field",
 *     text_scorer="BM25STD",
 *     filter_expression=None,
 *     alpha=0.7,
 *     dtype="float32",
 *     num_results=10,
 *     return_fields=["field1", "field2"],
 *     stopwords="english",
 *     dialect=2,
 * )
 * results = index.query(query)
 * </pre>
 *
 * <p>Java equivalent:
 *
 * <pre>
 * AggregateHybridQuery query = AggregateHybridQuery.builder()
 *     .text("example text")
 *     .textFieldName("text_field")
 *     .vector(new float[]{0.1f, 0.2f, 0.3f})
 *     .vectorFieldName("vector_field")
 *     .textScorer("BM25STD")
 *     .filterExpression(null)
 *     .alpha(0.7f)
 *     .dtype("float32")
 *     .numResults(10)
 *     .returnFields(List.of("field1", "field2"))
 *     .stopwords(AggregateHybridQuery.loadDefaultStopwords("english"))
 *     .dialect(2)
 *     .build();
 * List&lt;Map&lt;String, Object&gt;&gt; results = index.query(query);
 * </pre>
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 *
 * @since 0.1.0
 */
public final class AggregateHybridQuery extends HybridQuery {

  // Private constructor delegates to parent
  private AggregateHybridQuery(AggregateHybridQueryBuilder builder) {
    super(builder.toHybridQueryBuilder());
  }

  /**
   * Create a new builder for AggregateHybridQuery.
   *
   * @return A new AggregateHybridQueryBuilder instance
   */
  public static AggregateHybridQueryBuilder builder() {
    return new AggregateHybridQueryBuilder();
  }

  /** Builder for creating AggregateHybridQuery instances with fluent API. */
  public static class AggregateHybridQueryBuilder {
    private String text;
    private String textFieldName;
    private float[] vector;
    private String vectorFieldName;
    private String textScorer = "BM25STD";
    private Object filterExpression;
    private float alpha = 0.7f;
    private String dtype = "float32";
    private int numResults = 10;
    private List<String> returnFields = List.of();
    private Set<String> stopwords = loadDefaultStopwords("english");
    private int dialect = 2;

    /** Package-private constructor used by builder() method. */
    AggregateHybridQueryBuilder() {}

    /**
     * Set the text query string.
     *
     * @param text The text to search for
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder text(String text) {
      this.text = text;
      return this;
    }

    /**
     * Set the name of the text field to search.
     *
     * @param textFieldName The field name containing text data
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder textFieldName(String textFieldName) {
      this.textFieldName = textFieldName;
      return this;
    }

    /**
     * Set the query vector for similarity search. Makes a defensive copy.
     *
     * @param vector The embedding vector to search with
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null;
      return this;
    }

    /**
     * Set the name of the vector field to search.
     *
     * @param vectorFieldName The field name containing vector data
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder vectorFieldName(String vectorFieldName) {
      this.vectorFieldName = vectorFieldName;
      return this;
    }

    /**
     * Set the scoring algorithm for text search.
     *
     * @param textScorer The text scorer (e.g., "BM25", "TFIDF")
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder textScorer(String textScorer) {
      this.textScorer = textScorer;
      return this;
    }

    /**
     * Set an additional filter expression for the query using a Filter object.
     *
     * @param filterExpression The filter to apply
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set an additional filter expression for the query using a raw Redis query string.
     *
     * @param filterExpression The raw Redis filter string
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder filterExpression(String filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set the weight for combining text and vector scores.
     *
     * @param alpha Weight between 0.0 (text only) and 1.0 (vector only), default 0.7
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder alpha(float alpha) {
      this.alpha = alpha;
      return this;
    }

    /**
     * Set the data type for vector storage.
     *
     * @param dtype The data type (e.g., "float32", "float64")
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder dtype(String dtype) {
      this.dtype = dtype;
      return this;
    }

    /**
     * Set the maximum number of results to return.
     *
     * @param numResults The result limit
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the fields to return in results. Makes a defensive copy.
     *
     * @param returnFields List of field names to return
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder returnFields(List<String> returnFields) {
      this.returnFields = returnFields != null ? List.copyOf(returnFields) : List.of();
      return this;
    }

    /**
     * Set custom stopwords for text search. Makes a defensive copy.
     *
     * @param stopwords Set of words to exclude from text search
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder stopwords(Set<String> stopwords) {
      this.stopwords = stopwords != null ? Set.copyOf(stopwords) : Set.of();
      return this;
    }

    /**
     * Set the query dialect version.
     *
     * @param dialect The dialect version (default 2)
     * @return This builder for chaining
     */
    public AggregateHybridQueryBuilder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    /**
     * Build the AggregateHybridQuery instance.
     *
     * @return The configured AggregateHybridQuery
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public AggregateHybridQuery build() {
      return new AggregateHybridQuery(this);
    }

    /**
     * Convert this builder to a HybridQuery.HybridQueryBuilder for delegation.
     *
     * @return A HybridQueryBuilder with the same configuration
     */
    HybridQuery.HybridQueryBuilder toHybridQueryBuilder() {
      return HybridQuery.builder()
          .text(text)
          .textFieldName(textFieldName)
          .vector(vector)
          .vectorFieldName(vectorFieldName)
          .textScorer(textScorer)
          .filterExpression(filterExpression)
          .alpha(alpha)
          .dtype(dtype)
          .numResults(numResults)
          .returnFields(returnFields)
          .stopwords(stopwords)
          .dialect(dialect);
    }
  }
}
