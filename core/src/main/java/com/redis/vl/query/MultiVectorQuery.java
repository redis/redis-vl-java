package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import java.util.*;
import lombok.Getter;

/**
 * MultiVectorQuery allows for search over multiple vector fields in a document simultaneously.
 *
 * <p>The final score will be a weighted combination of the individual vector similarity scores
 * following the formula:
 *
 * <p>score = (w_1 * score_1 + w_2 * score_2 + w_3 * score_3 + ... )
 *
 * <p>Vectors may be of different size and datatype, but must be indexed using the 'cosine'
 * distance_metric.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:257-400 (MultiVectorQuery class)
 *
 * <p>Python equivalent:
 *
 * <pre>
 * from redisvl.query import MultiVectorQuery, Vector
 *
 * vector_1 = Vector(vector=[0.1, 0.2, 0.3], field_name="text_vector", dtype="float32", weight=0.7)
 * vector_2 = Vector(vector=[0.5, 0.5], field_name="image_vector", dtype="bfloat16", weight=0.2)
 *
 * query = MultiVectorQuery(
 *     vectors=[vector_1, vector_2],
 *     filter_expression=None,
 *     num_results=10,
 *     return_fields=["field1", "field2"],
 *     dialect=2
 * )
 * </pre>
 *
 * Java equivalent:
 *
 * <pre>
 * Vector vector1 = Vector.builder()
 *     .vector(new float[]{0.1f, 0.2f, 0.3f})
 *     .fieldName("text_vector")
 *     .dtype("float32")
 *     .weight(0.7)
 *     .build();
 *
 * Vector vector2 = Vector.builder()
 *     .vector(new float[]{0.5f, 0.5f})
 *     .fieldName("image_vector")
 *     .dtype("bfloat16")
 *     .weight(0.2)
 *     .build();
 *
 * MultiVectorQuery query = MultiVectorQuery.builder()
 *     .vectors(Arrays.asList(vector1, vector2))
 *     .numResults(10)
 *     .returnFields(Arrays.asList("field1", "field2"))
 *     .build();
 * </pre>
 */
@Getter
public final class MultiVectorQuery {

  /** Distance threshold for VECTOR_RANGE (hardcoded at 2.0 to include all eligible documents) */
  private static final double DISTANCE_THRESHOLD = 2.0;

  private final List<Vector> vectors;
  private final Filter filterExpression;
  private final List<String> returnFields;
  private final int numResults;
  private final int dialect;

  private MultiVectorQuery(Builder builder) {
    // Validate before modifying state
    if (builder.vectors == null || builder.vectors.isEmpty()) {
      throw new IllegalArgumentException("At least one Vector is required");
    }

    // Validate all elements are Vector objects
    for (Vector v : builder.vectors) {
      if (v == null) {
        throw new IllegalArgumentException("Vector list cannot contain null values");
      }
    }

    this.vectors = List.copyOf(builder.vectors);
    this.filterExpression = builder.filterExpression;
    this.returnFields =
        builder.returnFields != null ? List.copyOf(builder.returnFields) : List.of();
    this.numResults = builder.numResults;
    this.dialect = builder.dialect;
  }

  /**
   * Create a new Builder for MultiVectorQuery.
   *
   * @return A new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build the Redis query string for multi-vector search.
   *
   * <p>Format: {@code @field1:[VECTOR_RANGE 2.0 $vector_0]=>{$YIELD_DISTANCE_AS: distance_0} |
   * @field2:[VECTOR_RANGE 2.0 $vector_1]=>{$YIELD_DISTANCE_AS: distance_1}}
   *
   * @return Query string
   */
  public String toQueryString() {
    List<String> rangeQueries = new ArrayList<>();

    for (int i = 0; i < vectors.size(); i++) {
      Vector v = vectors.get(i);
      String rangeQuery =
          String.format(
              "@%s:[VECTOR_RANGE %.1f $vector_%d]=>{$YIELD_DISTANCE_AS: distance_%d}",
              v.getFieldName(), DISTANCE_THRESHOLD, i, i);
      rangeQueries.add(rangeQuery);
    }

    String baseQuery = String.join(" | ", rangeQueries);

    // Add filter expression if present
    if (filterExpression != null) {
      String filterStr = filterExpression.build();
      return String.format("(%s) AND (%s)", baseQuery, filterStr);
    }

    return baseQuery;
  }

  /**
   * Convert to parameter map for query execution.
   *
   * <p>Returns map with vector_0, vector_1, etc. as byte arrays
   *
   * @return Parameters map
   */
  public Map<String, Object> toParams() {
    Map<String, Object> params = new HashMap<>();

    for (int i = 0; i < vectors.size(); i++) {
      Vector v = vectors.get(i);
      byte[] vectorBytes = ArrayUtils.floatArrayToBytes(v.getVector());
      params.put(String.format("vector_%d", i), vectorBytes);
    }

    return params;
  }

  /**
   * Get the scoring formula for combining vector similarities.
   *
   * <p>Formula: w_1 * score_1 + w_2 * score_2 + ...
   *
   * <p>Where score_i = (2 - distance_i) / 2
   *
   * @return Scoring formula string
   */
  public String getScoringFormula() {
    List<String> scoreTerms = new ArrayList<>();

    for (int i = 0; i < vectors.size(); i++) {
      Vector v = vectors.get(i);
      scoreTerms.add(String.format("%.2f * score_%d", v.getWeight(), i));
    }

    return String.join(" + ", scoreTerms);
  }

  /**
   * Get individual score calculations.
   *
   * <p>Returns map of score_0=(2-distance_0)/2, score_1=(2-distance_1)/2, etc.
   *
   * @return Map of score names to calculation formulas
   */
  public Map<String, String> getScoreCalculations() {
    Map<String, String> calculations = new LinkedHashMap<>();

    for (int i = 0; i < vectors.size(); i++) {
      calculations.put(String.format("score_%d", i), String.format("(2 - distance_%d)/2", i));
    }

    return calculations;
  }

  @Override
  public String toString() {
    return toQueryString();
  }

  /** Builder for creating MultiVectorQuery instances. */
  public static class Builder {
    private List<Vector> vectors;
    private Filter filterExpression;
    private List<String> returnFields;
    private int numResults = 10; // Default from Python
    private int dialect = 2; // Default from Python

    Builder() {}

    /**
     * Set the vectors to search (accepts a single Vector).
     *
     * @param vector Single Vector for search
     * @return This builder
     */
    public Builder vector(Vector vector) {
      this.vectors = vector != null ? List.of(vector) : null;
      return this;
    }

    /**
     * Set the vectors to search (accepts multiple Vectors as varargs).
     *
     * @param vectors Vectors for multi-vector search
     * @return This builder
     */
    public Builder vectors(Vector... vectors) {
      this.vectors = vectors != null ? Arrays.asList(vectors) : null;
      return this;
    }

    /**
     * Set the vectors to search (accepts a List of Vectors).
     *
     * @param vectors List of Vectors for multi-vector search
     * @return This builder
     */
    public Builder vectors(List<Vector> vectors) {
      this.vectors = vectors != null ? new ArrayList<>(vectors) : null;
      return this;
    }

    /**
     * Set the filter expression.
     *
     * @param filterExpression Filter to apply
     * @return This builder
     */
    public Builder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set the fields to return in results (varargs).
     *
     * @param fields Field names to return
     * @return This builder
     */
    public Builder returnFields(String... fields) {
      this.returnFields = Arrays.asList(fields);
      return this;
    }

    /**
     * Set the fields to return in results (list).
     *
     * @param fields List of field names to return
     * @return This builder
     */
    public Builder returnFields(List<String> fields) {
      this.returnFields = fields != null ? new ArrayList<>(fields) : null;
      return this;
    }

    /**
     * Set the maximum number of results to return.
     *
     * @param numResults Maximum number of results
     * @return This builder
     */
    public Builder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the query dialect.
     *
     * @param dialect RediSearch dialect version
     * @return This builder
     */
    public Builder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    /**
     * Build the MultiVectorQuery instance.
     *
     * @return Configured MultiVectorQuery
     * @throws IllegalArgumentException if vectors is null/empty or contains null values
     */
    public MultiVectorQuery build() {
      return new MultiVectorQuery(this);
    }
  }
}
