package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import java.util.*;

/** Vector range query for finding vectors within a distance threshold */
public class VectorRangeQuery {

  private final float[] vector;
  private final String field;
  private final List<String> returnFields;
  private final int numResults;
  private final boolean returnScore;
  private final boolean normalizeVectorDistance;
  private double distanceThreshold;
  private Double epsilon;
  private final String sortBy;
  private final boolean sortDescending;
  private final boolean inOrder;

  private VectorRangeQuery(Builder builder) {
    // Validate before modifying state to avoid partial initialization
    if (builder.vector == null || builder.field == null) {
      throw new IllegalArgumentException("Vector and field are required");
    }
    if (builder.normalizeVectorDistance && builder.distanceThreshold > 1.0) {
      throw new IllegalArgumentException(
          "Distance threshold must be <= 1.0 when normalizing vector distance");
    }

    this.vector = Arrays.copyOf(builder.vector, builder.vector.length);
    this.field = builder.field;
    this.returnFields = builder.returnFields;
    this.distanceThreshold = builder.distanceThreshold;
    this.numResults = builder.numResults;
    this.returnScore = builder.returnScore;
    this.normalizeVectorDistance = builder.normalizeVectorDistance;
    this.epsilon = builder.epsilon;
    this.sortBy = builder.sortBy;
    this.sortDescending = builder.sortDescending;
    this.inOrder = builder.inOrder;
  }

  /**
   * Create a new Builder for VectorRangeQuery.
   *
   * @return A new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get the query vector.
   *
   * @return Copy of the query vector
   */
  public float[] getVector() {
    return vector != null ? Arrays.copyOf(vector, vector.length) : null;
  }

  /**
   * Get the field name to search.
   *
   * @return Vector field name
   */
  public String getField() {
    return field;
  }

  /**
   * Get the list of fields to return in results.
   *
   * @return Copy of return fields list or null
   */
  public List<String> getReturnFields() {
    return returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  /**
   * Get the distance threshold for range filtering.
   *
   * @return Distance threshold value
   */
  public double getDistanceThreshold() {
    return distanceThreshold;
  }

  /**
   * Set the distance threshold for range filtering.
   *
   * @param distanceThreshold New distance threshold value
   * @throws IllegalArgumentException if threshold > 1.0 when normalizing
   */
  public void setDistanceThreshold(double distanceThreshold) {
    if (normalizeVectorDistance && distanceThreshold > 1.0) {
      throw new IllegalArgumentException(
          "Distance threshold must be <= 1.0 when normalizing vector distance");
    }
    this.distanceThreshold = distanceThreshold;
  }

  /**
   * Get the maximum number of results to return.
   *
   * @return Number of results
   */
  public int getNumResults() {
    return numResults;
  }

  /**
   * Get the maximum number of results (deprecated alias).
   *
   * @deprecated Use getNumResults() instead
   * @return Number of results
   */
  @Deprecated
  public int getK() {
    return numResults;
  }

  /**
   * Check if scores should be returned with results.
   *
   * @return True if scores should be returned
   */
  public boolean isReturnScore() {
    return returnScore;
  }

  /**
   * Check if vector distances should be normalized.
   *
   * @return True if normalizing distances
   */
  public boolean isNormalizeVectorDistance() {
    return normalizeVectorDistance;
  }

  /**
   * Get the epsilon value for approximate search.
   *
   * @return Epsilon value or null
   */
  public Double getEpsilon() {
    return epsilon;
  }

  /**
   * Set the epsilon value for approximate search.
   *
   * @param epsilon Epsilon value for HNSW search
   */
  public void setEpsilon(double epsilon) {
    this.epsilon = epsilon;
  }

  /**
   * Get the field name to sort results by.
   *
   * @return Sort field name or null
   */
  public String getSortBy() {
    return sortBy;
  }

  /**
   * Check if results should be sorted in descending order.
   *
   * @return True if sorting descending
   */
  public boolean isSortDescending() {
    return sortDescending;
  }

  /**
   * Check if query terms must appear in order.
   *
   * @return True if enforcing term order
   */
  public boolean isInOrder() {
    return inOrder;
  }

  /**
   * Build the query string for Redis range query
   *
   * @return Query string
   */
  public String toQueryString() {
    // Use KNN syntax but filter by distance threshold afterward
    return "*=>[KNN " + numResults + " @" + field + " $vec AS distance]";
  }

  /**
   * Convert to parameter map for query execution
   *
   * @return Parameters map
   */
  public Map<String, Object> toParams() {
    Map<String, Object> params = new HashMap<>();

    // Convert vector to byte array
    if (vector != null) {
      byte[] vectorBytes = ArrayUtils.floatArrayToBytes(vector);
      params.put("vec", vectorBytes);
    }

    // Add distance threshold
    params.put("threshold", distanceThreshold);

    // Add epsilon if specified
    if (epsilon != null) {
      params.put("epsilon", epsilon);
    }

    return params;
  }

  @Override
  public String toString() {
    return toQueryString();
  }

  /** Builder for creating VectorRangeQuery instances. */
  public static class Builder {
    private float[] vector;
    private String field;
    private List<String> returnFields;
    private double distanceThreshold = 0.2;
    private int numResults = 10;
    private boolean returnScore = false;
    private boolean normalizeVectorDistance = false;
    private Double epsilon;
    private String sortBy;
    private boolean sortDescending = false;
    private boolean inOrder = false;

    /** Package-private constructor used by builder() method. */
    Builder() {}

    /**
     * Set the query vector.
     *
     * @param vector Query vector for similarity search
     * @return This builder
     */
    public Builder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null;
      return this;
    }

    /**
     * Set the vector field name to search.
     *
     * @param field Name of the vector field
     * @return This builder
     */
    public Builder field(String field) {
      this.field = field;
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
     * Set the distance threshold for range filtering.
     *
     * @param threshold Maximum distance from query vector
     * @return This builder
     */
    public Builder distanceThreshold(double threshold) {
      this.distanceThreshold = threshold;
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
     * Set the maximum number of results (deprecated alias).
     *
     * @deprecated Use numResults() instead
     * @param k Maximum number of results
     * @return This builder
     */
    @Deprecated
    public Builder k(int k) {
      this.numResults = k;
      return this;
    }

    /**
     * Set whether to return scores with results.
     *
     * @param returnScore True to return similarity scores
     * @return This builder
     */
    public Builder returnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    /**
     * Set whether to normalize vector distances.
     *
     * @param normalize True to normalize distances to [0, 1]
     * @return This builder
     */
    public Builder normalizeVectorDistance(boolean normalize) {
      this.normalizeVectorDistance = normalize;
      return this;
    }

    /**
     * Set the epsilon value for approximate HNSW search.
     *
     * @param epsilon Epsilon parameter for search precision
     * @return This builder
     */
    public Builder epsilon(double epsilon) {
      this.epsilon = epsilon;
      return this;
    }

    /**
     * Set the field name to sort results by.
     *
     * @param sortBy Field name for sorting
     * @return This builder
     */
    public Builder sortBy(String sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    /**
     * Set whether to sort results in descending order.
     *
     * @param descending True for descending sort
     * @return This builder
     */
    public Builder sortDescending(boolean descending) {
      this.sortDescending = descending;
      return this;
    }

    /**
     * Set whether query terms must appear in order.
     *
     * @param inOrder True to enforce term order
     * @return This builder
     */
    public Builder inOrder(boolean inOrder) {
      this.inOrder = inOrder;
      return this;
    }

    /**
     * Build the VectorRangeQuery instance.
     *
     * @return Configured VectorRangeQuery
     * @throws IllegalArgumentException if vector or field is null
     */
    public VectorRangeQuery build() {
      return new VectorRangeQuery(this);
    }
  }
}
