package com.redis.vl.query;

import com.redis.vl.schema.VectorField;
import com.redis.vl.utils.ArrayUtils;
import java.util.*;

/** Represents a vector similarity search query */
public class VectorQuery {

  /** Field name for the vector field */
  private final String field;

  /** Query vector as float array */
  private final float[] vector;

  /** Number of nearest neighbors to return */
  private final int numResults;

  /** Distance metric to use */
  private final VectorField.DistanceMetric distanceMetric;

  /** Whether to return distance values */
  private final boolean returnDistance;

  /** Whether to return similarity scores */
  private final boolean returnScore;

  /** Optional pre-filter for the search */
  private final String preFilter;

  /** Field for hybrid search */
  private final String hybridField;

  /** Query text for hybrid search */
  private final String hybridQuery;

  /** Fields to return in results */
  private final List<String> returnFields;

  /** Whether to normalize vector distance */
  private final boolean normalizeVectorDistance;

  /** EF runtime parameter for HNSW algorithm */
  private Integer efRuntime;

  /** Search window size for SVS-VAMANA algorithm (Python PR #439) */
  private Integer searchWindowSize;

  /** Search history usage for SVS-VAMANA algorithm: OFF, ON, AUTO (Python PR #439) */
  private String useSearchHistory;

  /** Search buffer capacity for SVS-VAMANA algorithm (Python PR #439) */
  private Integer searchBufferCapacity;

  /** Filter query for pre-filtering */
  private Filter filter;

  /** Hybrid policy for vector search */
  private String hybridPolicy;

  /** Batch size for hybrid search */
  private Integer batchSize;

  /** Field to sort results by */
  private String sortBy;

  /** Whether to sort in descending order (default: ascending) */
  private boolean sortDescending;

  /** Whether to require terms in field to have same order as in query */
  private boolean inOrder;

  /** Fields that should not be decoded from binary format */
  private List<String> skipDecodeFields;

  /** Private constructor */
  private VectorQuery(
      String field,
      float[] vector,
      int numResults,
      VectorField.DistanceMetric distanceMetric,
      boolean returnDistance,
      boolean returnScore,
      String preFilter,
      String hybridField,
      String hybridQuery,
      Integer efRuntime,
      Integer searchWindowSize,
      String useSearchHistory,
      Integer searchBufferCapacity,
      List<String> returnFields,
      boolean normalizeVectorDistance,
      String sortBy,
      boolean sortDescending,
      boolean inOrder,
      List<String> skipDecodeFields) {
    this.field = field;
    this.vector = vector != null ? vector.clone() : null; // Defensive copy
    this.numResults = numResults;
    this.distanceMetric = distanceMetric;
    this.returnDistance = returnDistance;
    this.returnScore = returnScore;
    this.preFilter = preFilter;
    this.hybridField = hybridField;
    this.hybridQuery = hybridQuery;
    this.efRuntime = efRuntime;
    this.searchWindowSize = searchWindowSize;
    this.useSearchHistory = useSearchHistory;
    this.searchBufferCapacity = searchBufferCapacity;
    this.returnFields = returnFields;
    this.normalizeVectorDistance = normalizeVectorDistance;
    this.sortBy = sortBy;
    this.sortDescending = sortDescending;
    this.inOrder = inOrder;
    this.skipDecodeFields =
        skipDecodeFields != null ? new ArrayList<>(skipDecodeFields) : new ArrayList<>();
  }

  /**
   * Create a builder
   *
   * @return Vector query builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a VectorQuery with fluent API for float array
   *
   * @param field Vector field name
   * @param vector Query vector
   * @return Vector query builder
   */
  public static Builder of(String field, float[] vector) {
    return builder().field(field).vector(vector);
  }

  /**
   * Create a VectorQuery with fluent API for double array
   *
   * @param field Vector field name
   * @param vector Query vector
   * @return Vector query builder
   */
  public static Builder of(String field, double[] vector) {
    return builder().field(field).vector(toFloatArray(vector));
  }

  /** Convert double array to float array */
  private static float[] toFloatArray(double[] doubles) {
    if (doubles == null) return new float[0];
    float[] floats = new float[doubles.length];
    for (int i = 0; i < doubles.length; i++) {
      floats[i] = (float) doubles[i];
    }
    return floats;
  }

  /** Escape field name for RediSearch query */
  private static String escapeFieldName(String field) {
    if (field == null) return null;

    // For JSONPath fields in RediSearch, we need to escape special characters
    // RediSearch expects double backslash escaping for $ and . in field names
    // e.g., $.embedding becomes \\$\\.embedding in the query string
    if (field.startsWith("$.")) {
      // Escape $ and . characters with double backslash for Java string literal
      // This produces \$ and \. in the actual query sent to Redis
      return field.replace("$", "\\$").replace(".", "\\.");
    }
    return field;
  }

  /**
   * Create a copy with modified numResults value
   *
   * @param newNumResults New number of results to return
   * @return New VectorQuery instance
   */
  public VectorQuery withNumResults(int newNumResults) {
    return new Builder()
        .field(this.field)
        .vector(this.vector)
        .numResults(newNumResults)
        .distanceMetric(this.distanceMetric)
        .returnDistance(this.returnDistance)
        .returnScore(this.returnScore)
        .preFilter(this.preFilter)
        .hybridField(this.hybridField)
        .hybridQuery(this.hybridQuery)
        .efRuntime(this.efRuntime)
        .build();
  }

  /**
   * Create a copy with modified K value
   *
   * @deprecated Use withNumResults() instead
   * @param newK New K value
   * @return New VectorQuery instance
   */
  @Deprecated
  public VectorQuery withK(int newK) {
    return withNumResults(newK);
  }

  /**
   * Create a copy with modified distance metric
   *
   * @param metric New distance metric
   * @return New VectorQuery instance
   */
  public VectorQuery withDistanceMetric(VectorField.DistanceMetric metric) {
    return new Builder()
        .field(this.field)
        .vector(this.vector)
        .numResults(this.numResults)
        .distanceMetric(metric)
        .returnDistance(this.returnDistance)
        .returnScore(this.returnScore)
        .preFilter(this.preFilter)
        .hybridField(this.hybridField)
        .hybridQuery(this.hybridQuery)
        .efRuntime(this.efRuntime)
        .build();
  }

  /**
   * Create a copy with modified pre-filter
   *
   * @param filter New pre-filter expression
   * @return New VectorQuery instance
   */
  public VectorQuery withPreFilter(String filter) {
    return new Builder()
        .field(this.field)
        .vector(this.vector)
        .numResults(this.numResults)
        .distanceMetric(this.distanceMetric)
        .returnDistance(this.returnDistance)
        .returnScore(this.returnScore)
        .preFilter(filter)
        .hybridField(this.hybridField)
        .hybridQuery(this.hybridQuery)
        .efRuntime(this.efRuntime)
        .build();
  }

  /**
   * Build the query string for Redis
   *
   * @return Query string
   */
  public String toQueryString() {
    StringBuilder query = new StringBuilder();

    // Build base query - check both preFilter and filter fields
    String filterExpression = null;

    if (filter != null) {
      filterExpression = filter.build();
    } else if (preFilter != null && !preFilter.isEmpty()) {
      filterExpression = preFilter;
    }

    if (filterExpression != null && !filterExpression.isEmpty()) {
      query.append("(").append(filterExpression).append(")");
    } else {
      query.append("*");
    }

    // Add hybrid text search if specified
    if (hybridField != null && hybridQuery != null) {
      if (query.length() > 1) { // If we already have content beyond "*"
        query.append(" ");
      }
      query.append("@").append(hybridField).append(":(").append(hybridQuery).append(")");
    }

    // Build KNN query - RediSearch format: (*=>[KNN $K @field $vec AS alias])
    // For JSONPath fields, escape special characters for RediSearch
    String escapedField = escapeFieldName(field);
    query.append("=>[KNN $K @").append(escapedField).append(" $vec");

    // Add AS alias if returnDistance is true
    if (returnDistance) {
      query.append(" AS vector_distance");
    }

    query.append("]");

    return query.toString();
  }

  /**
   * Convert to parameter map for query execution
   *
   * @return Parameters map
   */
  public Map<String, Object> toParams() {
    Map<String, Object> params = new HashMap<>();

    // Add K parameter for KNN
    params.put("K", numResults);

    // Convert vector to byte array
    byte[] vectorBytes = ArrayUtils.floatArrayToBytes(vector);
    params.put("vec", vectorBytes);

    // Add runtime parameters if specified
    // EF runtime for HNSW
    if (efRuntime != null) {
      params.put("ef_runtime", efRuntime);
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

  // Getters
  /**
   * Get the vector field name
   *
   * @return Field name
   */
  public String getField() {
    return field;
  }

  /**
   * Get the query vector
   *
   * @return Query vector as float array
   */
  public float[] getVector() {
    return vector != null ? vector.clone() : null; // Defensive copy
  }

  /**
   * Get the number of results to return
   *
   * @return Number of results
   */
  public int getNumResults() {
    return numResults;
  }

  /**
   * Get the K value (number of results)
   *
   * @deprecated Use getNumResults() instead
   * @return Number of results
   */
  @Deprecated
  public int getK() {
    return numResults;
  }

  /**
   * Get the distance metric
   *
   * @return Distance metric
   */
  public VectorField.DistanceMetric getDistanceMetric() {
    return distanceMetric;
  }

  /**
   * Check if distance values should be returned
   *
   * @return True if distance values should be returned
   */
  public boolean isReturnDistance() {
    return returnDistance;
  }

  /**
   * Check if similarity scores should be returned
   *
   * @return True if scores should be returned
   */
  public boolean isReturnScore() {
    return returnScore;
  }

  /**
   * Get the pre-filter expression
   *
   * @return Pre-filter expression
   */
  public String getPreFilter() {
    return preFilter;
  }

  /**
   * Get the hybrid search field
   *
   * @return Hybrid field name
   */
  public String getHybridField() {
    return hybridField;
  }

  /**
   * Get the hybrid search query
   *
   * @return Hybrid query string
   */
  public String getHybridQuery() {
    return hybridQuery;
  }

  /**
   * Get the EF runtime parameter for HNSW
   *
   * @return EF runtime value
   */
  public Integer getEfRuntime() {
    return efRuntime;
  }

  /**
   * Set the EF runtime parameter for HNSW
   *
   * @param efRuntime EF runtime value
   * @throws IllegalArgumentException if efRuntime is not positive
   */
  public void setEfRuntime(int efRuntime) {
    if (efRuntime <= 0) {
      throw new IllegalArgumentException("efRuntime must be positive");
    }
    this.efRuntime = efRuntime;
  }

  /**
   * Get the search window size for SVS-VAMANA
   *
   * @return Search window size value
   */
  public Integer getSearchWindowSize() {
    return searchWindowSize;
  }

  /**
   * Set the search window size parameter for SVS-VAMANA
   *
   * @param searchWindowSize Search window size value
   * @throws IllegalArgumentException if searchWindowSize is not positive
   */
  public void setSearchWindowSize(int searchWindowSize) {
    if (searchWindowSize <= 0) {
      throw new IllegalArgumentException("searchWindowSize must be positive");
    }
    this.searchWindowSize = searchWindowSize;
  }

  /**
   * Get the use search history parameter for SVS-VAMANA
   *
   * @return Use search history value (OFF, ON, or AUTO)
   */
  public String getUseSearchHistory() {
    return useSearchHistory;
  }

  /**
   * Set the use search history parameter for SVS-VAMANA
   *
   * @param useSearchHistory Use search history value (must be OFF, ON, or AUTO)
   * @throws IllegalArgumentException if useSearchHistory is not one of the allowed values
   */
  public void setUseSearchHistory(String useSearchHistory) {
    if (useSearchHistory != null && !useSearchHistory.matches("OFF|ON|AUTO")) {
      throw new IllegalArgumentException("useSearchHistory must be one of: OFF, ON, AUTO");
    }
    this.useSearchHistory = useSearchHistory;
  }

  /**
   * Get the search buffer capacity for SVS-VAMANA
   *
   * @return Search buffer capacity value
   */
  public Integer getSearchBufferCapacity() {
    return searchBufferCapacity;
  }

  /**
   * Set the search buffer capacity parameter for SVS-VAMANA
   *
   * @param searchBufferCapacity Search buffer capacity value
   * @throws IllegalArgumentException if searchBufferCapacity is not positive
   */
  public void setSearchBufferCapacity(int searchBufferCapacity) {
    if (searchBufferCapacity <= 0) {
      throw new IllegalArgumentException("searchBufferCapacity must be positive");
    }
    this.searchBufferCapacity = searchBufferCapacity;
  }

  /**
   * Get the fields to return in results
   *
   * @return List of field names
   */
  public List<String> getReturnFields() {
    return returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  /**
   * Get the filter query
   *
   * @return Filter instance
   */
  public Filter getFilter() {
    return filter;
  }

  /**
   * Set the filter query
   *
   * @param filter Filter instance
   */
  public void setFilter(Filter filter) {
    this.filter = filter;
  }

  /**
   * Get the hybrid search policy
   *
   * @return Hybrid policy
   */
  public String getHybridPolicy() {
    return hybridPolicy;
  }

  /**
   * Set the hybrid search policy
   *
   * @param policy Hybrid policy
   */
  public void setHybridPolicy(String policy) {
    this.hybridPolicy = policy;
  }

  /**
   * Get the batch size
   *
   * @return Batch size
   */
  public Integer getBatchSize() {
    return batchSize;
  }

  /**
   * Set the batch size
   *
   * @param batchSize Batch size
   */
  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  /**
   * Check if vector distance should be normalized
   *
   * @return True if normalization is enabled
   */
  public boolean isNormalizeVectorDistance() {
    return normalizeVectorDistance;
  }

  /**
   * Get the sort field
   *
   * @return Sort field name
   */
  public String getSortBy() {
    return sortBy;
  }

  /**
   * Check if sorting is descending
   *
   * @return True if descending
   */
  public boolean isSortDescending() {
    return sortDescending;
  }

  /**
   * Check if terms must be in order
   *
   * @return True if in-order is required
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
    return skipDecodeFields != null ? new ArrayList<>(skipDecodeFields) : new ArrayList<>();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("VectorQuery{");
    sb.append("field='").append(field).append("'");
    sb.append(", numResults=").append(numResults);
    if (filter != null) {
      sb.append(", filter='").append(filter.build()).append("'");
    }
    if (hybridPolicy != null) {
      sb.append(", HYBRID_POLICY ").append(hybridPolicy);
      if (batchSize != null) {
        sb.append(" BATCH_SIZE ").append(batchSize);
      }
    }
    if (efRuntime != null) {
      sb.append(", EF_RUNTIME $EF_RUNTIME");
    }
    sb.append("}");
    return sb.toString();
  }

  /** Builder for VectorQuery */
  public static class Builder {
    private String field;
    private float[] vector;
    private int numResults = 10;
    private VectorField.DistanceMetric distanceMetric = VectorField.DistanceMetric.COSINE;
    private boolean returnDistance = true;
    private boolean returnScore = false;
    private String preFilter;

    /** Create a new Builder instance */
    public Builder() {
      // Default constructor
    }

    private String hybridField;
    private String hybridQuery;
    private Integer efRuntime;
    private Integer searchWindowSize;
    private String useSearchHistory;
    private Integer searchBufferCapacity;
    private List<String> returnFields;
    private boolean normalizeVectorDistance = false;
    private String sortBy;
    private boolean sortDescending = false;
    private boolean inOrder = false;
    private List<String> skipDecodeFields = new ArrayList<>();

    private static float[] toFloatArray(double[] doubles) {
      if (doubles == null) return null;
      float[] floats = new float[doubles.length];
      for (int i = 0; i < doubles.length; i++) {
        floats[i] = (float) doubles[i];
      }
      return floats;
    }

    /**
     * Set the vector field name
     *
     * @param field Field name
     * @return This builder
     */
    public Builder field(String field) {
      this.field = field;
      return this;
    }

    /**
     * Set the query vector
     *
     * @param vector Query vector as float array
     * @return This builder
     */
    public Builder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null; // Defensive copy
      return this;
    }

    /**
     * Set the query vector
     *
     * @param vector Query vector as double array
     * @return This builder
     */
    public Builder vector(double[] vector) {
      this.vector = toFloatArray(vector);
      return this;
    }

    /**
     * Set the number of results to return
     *
     * @param numResults Number of results
     * @return This builder
     */
    public Builder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the K value (number of results)
     *
     * @deprecated Use numResults() instead
     * @param k Number of results
     * @return This builder
     */
    @Deprecated
    public Builder k(int k) {
      this.numResults = k;
      return this;
    }

    /**
     * Set the K value (number of results)
     *
     * @deprecated Use numResults() instead
     * @param k Number of results
     * @return This builder
     */
    @Deprecated
    public Builder withK(int k) {
      this.numResults = k;
      return this;
    }

    /**
     * Set the distance metric
     *
     * @param distanceMetric Distance metric
     * @return This builder
     */
    public Builder distanceMetric(VectorField.DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    /**
     * Set the distance metric (alternative method)
     *
     * @param metric Distance metric
     * @return This builder
     */
    public Builder withDistanceMetric(VectorField.DistanceMetric metric) {
      this.distanceMetric = metric;
      return this;
    }

    /**
     * Set whether to return distance values
     *
     * @param returnDistance True to return distances
     * @return This builder
     */
    public Builder returnDistance(boolean returnDistance) {
      this.returnDistance = returnDistance;
      return this;
    }

    /**
     * Set whether to return distance values (alternative method)
     *
     * @param returnDistance True to return distances
     * @return This builder
     */
    public Builder withReturnDistance(boolean returnDistance) {
      this.returnDistance = returnDistance;
      return this;
    }

    /**
     * Set whether to return similarity scores
     *
     * @param returnScore True to return scores
     * @return This builder
     */
    public Builder returnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    /**
     * Set whether to return similarity scores (alternative method)
     *
     * @param returnScore True to return scores
     * @return This builder
     */
    public Builder withReturnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    /**
     * Set the pre-filter expression
     *
     * @param preFilter Pre-filter expression
     * @return This builder
     */
    public Builder preFilter(String preFilter) {
      this.preFilter = preFilter;
      return this;
    }

    /**
     * Set the pre-filter expression (alternative method)
     *
     * @param filter Pre-filter expression
     * @return This builder
     */
    public Builder withPreFilter(String filter) {
      this.preFilter = filter;
      return this;
    }

    /**
     * Set the hybrid search field
     *
     * @param hybridField Hybrid field name
     * @return This builder
     */
    public Builder hybridField(String hybridField) {
      this.hybridField = hybridField;
      return this;
    }

    /**
     * Set the hybrid search query
     *
     * @param hybridQuery Hybrid query string
     * @return This builder
     */
    public Builder hybridQuery(String hybridQuery) {
      this.hybridQuery = hybridQuery;
      return this;
    }

    /**
     * Set hybrid search parameters
     *
     * @param field Hybrid field name
     * @param query Hybrid query string
     * @return This builder
     */
    public Builder withHybridSearch(String field, String query) {
      this.hybridField = field;
      this.hybridQuery = query;
      return this;
    }

    /**
     * Set the EF runtime parameter for HNSW
     *
     * @param efRuntime EF runtime value
     * @return This builder
     * @throws IllegalArgumentException if efRuntime is not positive
     */
    public Builder efRuntime(Integer efRuntime) {
      if (efRuntime != null && efRuntime <= 0) {
        throw new IllegalArgumentException("efRuntime must be positive");
      }
      this.efRuntime = efRuntime;
      return this;
    }

    /**
     * Set the EF runtime parameter for HNSW (alternative method)
     *
     * @param efRuntime EF runtime value
     * @return This builder
     * @throws IllegalArgumentException if efRuntime is not positive
     */
    public Builder withEfRuntime(int efRuntime) {
      if (efRuntime <= 0) {
        throw new IllegalArgumentException("efRuntime must be positive");
      }
      this.efRuntime = efRuntime;
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
     * Set the fields to return in results
     *
     * @param fields Field names
     * @return This builder
     */
    public Builder returnFields(String... fields) {
      this.returnFields = Arrays.asList(fields);
      return this;
    }

    /**
     * Set the fields to return in results
     *
     * @param fields List of field names
     * @return This builder
     */
    public Builder returnFields(List<String> fields) {
      this.returnFields = fields != null ? new ArrayList<>(fields) : null;
      return this;
    }

    /**
     * Set whether to normalize vector distance
     *
     * @param normalize True to normalize
     * @return This builder
     */
    public Builder normalizeVectorDistance(boolean normalize) {
      this.normalizeVectorDistance = normalize;
      return this;
    }

    /**
     * Set the sort field (defaults to ascending).
     *
     * <p>Python equivalent: sort_by="price"
     *
     * @param sortBy Sort field name
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
     * Set whether to sort in descending order
     *
     * @param descending True for descending
     * @return This builder
     */
    public Builder sortDescending(boolean descending) {
      this.sortDescending = descending;
      return this;
    }

    /**
     * Set whether terms must be in order
     *
     * @param inOrder True for in-order matching
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
        this.skipDecodeFields = new ArrayList<>();
        return this;
      }
      // Validate no null values
      for (String field : skipDecodeFields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = new ArrayList<>(skipDecodeFields);
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
        this.skipDecodeFields = new ArrayList<>();
        return this;
      }
      for (String field : fields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = new ArrayList<>(Arrays.asList(fields));
      return this;
    }

    /**
     * Build the VectorQuery
     *
     * @return VectorQuery instance
     */
    public VectorQuery build() {
      // Validate required fields
      if (field == null || field.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name is required");
      }
      if (vector == null) {
        throw new IllegalArgumentException("Vector is required");
      }
      if (vector.length == 0) {
        throw new IllegalArgumentException("Vector cannot be empty");
      }
      if (numResults <= 0) {
        throw new IllegalArgumentException("numResults must be positive");
      }

      // Build the query
      return new VectorQuery(
          field.trim(),
          vector,
          numResults,
          distanceMetric,
          returnDistance,
          returnScore,
          preFilter,
          hybridField,
          hybridQuery,
          efRuntime,
          searchWindowSize,
          useSearchHistory,
          searchBufferCapacity,
          returnFields,
          normalizeVectorDistance,
          sortBy,
          sortDescending,
          inOrder,
          skipDecodeFields);
    }
  }
}
