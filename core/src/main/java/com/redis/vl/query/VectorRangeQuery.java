package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import java.util.*;

/**
 * Vector range query for finding vectors within a distance threshold.
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 */
public final class VectorRangeQuery {

  private final float[] vector;
  private final String field;
  private final List<String> returnFields;
  private final int numResults;
  private final boolean returnScore;
  private final boolean normalizeVectorDistance;
  private double distanceThreshold;
  private Double epsilon;

  /** Search window size for SVS-VAMANA algorithm (Python PR #439) */
  private Integer searchWindowSize;

  /** Search history usage for SVS-VAMANA algorithm: OFF, ON, AUTO (Python PR #439) */
  private String useSearchHistory;

  /** Search buffer capacity for SVS-VAMANA algorithm (Python PR #439) */
  private Integer searchBufferCapacity;

  private final String sortBy;
  private final boolean sortDescending;
  private final boolean inOrder;
  private final List<String> skipDecodeFields;

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
    this.searchWindowSize = builder.searchWindowSize;
    this.useSearchHistory = builder.useSearchHistory;
    this.searchBufferCapacity = builder.searchBufferCapacity;
    this.sortBy = builder.sortBy;
    this.sortDescending = builder.sortDescending;
    this.inOrder = builder.inOrder;
    this.skipDecodeFields =
        builder.skipDecodeFields != null ? List.copyOf(builder.skipDecodeFields) : List.of();
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
   * <p>Python PR #439: Runtime parameter for range query approximation. Must be non-negative.
   *
   * @param epsilon Epsilon value for range search (must be non-negative)
   * @throws IllegalArgumentException if epsilon is negative
   */
  public void setEpsilon(double epsilon) {
    if (epsilon < 0) {
      throw new IllegalArgumentException("epsilon must be non-negative");
    }
    this.epsilon = epsilon;
  }

  /**
   * Get the search window size for SVS-VAMANA algorithm.
   *
   * @return Search window size or null
   */
  public Integer getSearchWindowSize() {
    return searchWindowSize;
  }

  /**
   * Set the search window size for SVS-VAMANA algorithm.
   *
   * <p>Python PR #439: SVS-VAMANA runtime parameter for KNN search window.
   *
   * @param searchWindowSize Search window size (must be positive)
   * @throws IllegalArgumentException if searchWindowSize is not positive
   */
  public void setSearchWindowSize(int searchWindowSize) {
    if (searchWindowSize <= 0) {
      throw new IllegalArgumentException("searchWindowSize must be positive");
    }
    this.searchWindowSize = searchWindowSize;
  }

  /**
   * Get the use search history mode for SVS-VAMANA algorithm.
   *
   * @return Use search history mode or null
   */
  public String getUseSearchHistory() {
    return useSearchHistory;
  }

  /**
   * Set the use search history mode for SVS-VAMANA algorithm.
   *
   * <p>Python PR #439: SVS-VAMANA runtime parameter for search buffer control.
   *
   * @param useSearchHistory Search history mode (OFF, ON, or AUTO)
   * @throws IllegalArgumentException if not one of: OFF, ON, AUTO
   */
  public void setUseSearchHistory(String useSearchHistory) {
    if (useSearchHistory != null && !useSearchHistory.matches("OFF|ON|AUTO")) {
      throw new IllegalArgumentException("useSearchHistory must be one of: OFF, ON, AUTO");
    }
    this.useSearchHistory = useSearchHistory;
  }

  /**
   * Get the search buffer capacity for SVS-VAMANA algorithm.
   *
   * @return Search buffer capacity or null
   */
  public Integer getSearchBufferCapacity() {
    return searchBufferCapacity;
  }

  /**
   * Set the search buffer capacity for SVS-VAMANA algorithm.
   *
   * <p>Python PR #439: SVS-VAMANA runtime parameter for compression tuning.
   *
   * @param searchBufferCapacity Search buffer capacity (must be positive)
   * @throws IllegalArgumentException if searchBufferCapacity is not positive
   */
  public void setSearchBufferCapacity(int searchBufferCapacity) {
    if (searchBufferCapacity <= 0) {
      throw new IllegalArgumentException("searchBufferCapacity must be positive");
    }
    this.searchBufferCapacity = searchBufferCapacity;
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
   * Get the list of fields that should not be decoded from binary format.
   *
   * @return List of field names to skip decoding
   */
  public List<String> getSkipDecodeFields() {
    return skipDecodeFields;
  }

  /**
   * Build the query string for Redis range query
   *
   * @return Query string
   */
  public String toQueryString() {
    // Use VECTOR_RANGE syntax to filter by distance threshold (Python: line 685)
    // Format: @field:[VECTOR_RANGE $threshold $vec]=>{$YIELD_DISTANCE_AS: vector_distance}
    return "@" + field + ":[VECTOR_RANGE $threshold $vec]=>{$YIELD_DISTANCE_AS: vector_distance}";
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

    // Add distance threshold (Python: DISTANCE_THRESHOLD_PARAM)
    params.put("threshold", distanceThreshold);

    // Add runtime parameters if specified
    // Epsilon for range search approximation
    if (epsilon != null) {
      params.put("EPSILON", epsilon);
    }

    // SVS-VAMANA runtime parameters (Python PR #439)
    if (searchWindowSize != null) {
      params.put("search_window_size", searchWindowSize);
    }

    if (useSearchHistory != null) {
      params.put("use_search_history", useSearchHistory);
    }

    if (searchBufferCapacity != null) {
      params.put("search_buffer_capacity", searchBufferCapacity);
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
    private Integer searchWindowSize;
    private String useSearchHistory;
    private Integer searchBufferCapacity;
    private String sortBy;
    private boolean sortDescending = false;
    private boolean inOrder = false;
    private List<String> skipDecodeFields = List.of();

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
      if (epsilon < 0) {
        throw new IllegalArgumentException("epsilon must be non-negative");
      }
      this.epsilon = epsilon;
      return this;
    }

    /**
     * Set the search window size parameter for SVS-VAMANA algorithm.
     *
     * <p>Controls the KNN search window size. Must be positive.
     *
     * <p>Python PR #439: SVS-VAMANA runtime parameter support
     *
     * @param searchWindowSize Search window size (must be positive)
     * @return This builder
     * @throws IllegalArgumentException if searchWindowSize is not positive
     */
    public Builder searchWindowSize(Integer searchWindowSize) {
      if (searchWindowSize != null && searchWindowSize <= 0) {
        throw new IllegalArgumentException("searchWindowSize must be positive");
      }
      this.searchWindowSize = searchWindowSize;
      return this;
    }

    /**
     * Set the use search history parameter for SVS-VAMANA algorithm.
     *
     * <p>Controls search buffer usage. Valid values: "OFF", "ON", "AUTO"
     *
     * <p>Python PR #439: SVS-VAMANA runtime parameter support
     *
     * @param useSearchHistory Search history mode (OFF, ON, or AUTO)
     * @return This builder
     * @throws IllegalArgumentException if useSearchHistory is not one of: OFF, ON, AUTO
     */
    public Builder useSearchHistory(String useSearchHistory) {
      if (useSearchHistory != null && !useSearchHistory.matches("OFF|ON|AUTO")) {
        throw new IllegalArgumentException("useSearchHistory must be one of: OFF, ON, AUTO");
      }
      this.useSearchHistory = useSearchHistory;
      return this;
    }

    /**
     * Set the search buffer capacity parameter for SVS-VAMANA algorithm.
     *
     * <p>Controls compression tuning. Must be positive.
     *
     * <p>Python PR #439: SVS-VAMANA runtime parameter support
     *
     * @param searchBufferCapacity Search buffer capacity (must be positive)
     * @return This builder
     * @throws IllegalArgumentException if searchBufferCapacity is not positive
     */
    public Builder searchBufferCapacity(Integer searchBufferCapacity) {
      if (searchBufferCapacity != null && searchBufferCapacity <= 0) {
        throw new IllegalArgumentException("searchBufferCapacity must be positive");
      }
      this.searchBufferCapacity = searchBufferCapacity;
      return this;
    }

    /**
     * Set the field name to sort results by (defaults to ascending).
     *
     * <p>Python equivalent: sort_by="price"
     *
     * @param sortBy Field name for sorting
     * @return This builder
     */
    public Builder sortBy(String sortBy) {
      this.sortBy = sortBy;
      this.sortDescending = false; // Default to ascending
      return this;
    }

    /**
     * Set the sort field with explicit direction.
     *
     * <p>Python equivalent: sort_by=("price", "DESC")
     *
     * @param field Field name to sort by
     * @param direction Sort direction ("ASC" or "DESC", case-insensitive)
     * @return This builder
     * @throws IllegalArgumentException if direction is invalid
     */
    public Builder sortBy(String field, String direction) {
      List<SortField> parsed = SortSpec.parseSortSpec(field, direction);
      SortField sortField = parsed.get(0);
      this.sortBy = sortField.getFieldName();
      this.sortDescending = !sortField.isAscending();
      return this;
    }

    /**
     * Set the sort field using SortField.
     *
     * <p>Python equivalent: sort_by=("rating", "DESC") or using SortField.desc("rating")
     *
     * @param sortField SortField specifying field and direction
     * @return This builder
     * @throws IllegalArgumentException if sortField is null
     */
    public Builder sortBy(SortField sortField) {
      if (sortField == null) {
        throw new IllegalArgumentException("SortField cannot be null");
      }
      this.sortBy = sortField.getFieldName();
      this.sortDescending = !sortField.isAscending();
      return this;
    }

    /**
     * Set the sort fields (supports multiple fields, but only first is used).
     *
     * <p>Python equivalent: sort_by=[("price", "DESC"), ("rating", "ASC"), "stock"]
     *
     * <p>Note: Redis Search only supports single-field sorting. When multiple fields are provided,
     * only the first field is used and a warning is logged.
     *
     * @param sortFields List of SortFields
     * @return This builder
     */
    public Builder sortBy(List<SortField> sortFields) {
      List<SortField> parsed = SortSpec.parseSortSpec(sortFields);
      if (!parsed.isEmpty()) {
        SortField firstField = parsed.get(0);
        this.sortBy = firstField.getFieldName();
        this.sortDescending = !firstField.isAscending();
      } else {
        // Empty list - clear sorting
        this.sortBy = null;
      }
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
     * Set fields that should not be decoded from binary format.
     *
     * @param skipDecodeFields List of field names
     * @return This builder
     * @throws IllegalArgumentException if list contains null values
     */
    public Builder skipDecodeFields(List<String> skipDecodeFields) {
      if (skipDecodeFields == null) {
        this.skipDecodeFields = List.of();
        return this;
      }
      // Validate no null values
      for (String field : skipDecodeFields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = List.copyOf(skipDecodeFields);
      return this;
    }

    /**
     * Set fields that should not be decoded from binary format (varargs).
     *
     * @param fields Field names
     * @return This builder
     * @throws IllegalArgumentException if any field is null
     */
    public Builder skipDecodeFields(String... fields) {
      if (fields == null || fields.length == 0) {
        this.skipDecodeFields = List.of();
        return this;
      }
      for (String field : fields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = List.of(fields);
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
