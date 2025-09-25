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

  /** Filter query for pre-filtering */
  private Filter filter;

  /** Hybrid policy for vector search */
  private String hybridPolicy;

  /** Batch size for hybrid search */
  private Integer batchSize;

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
      List<String> returnFields,
      boolean normalizeVectorDistance) {
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
    this.returnFields = returnFields;
    this.normalizeVectorDistance = normalizeVectorDistance;
  }

  /** Create a builder */
  public static Builder builder() {
    return new Builder();
  }

  /** Create a VectorQuery with fluent API for float array */
  public static Builder of(String field, float[] vector) {
    return builder().field(field).vector(vector);
  }

  /** Create a VectorQuery with fluent API for double array */
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

  /** Create a copy with modified numResults value */
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
   * @deprecated Use withNumResults() instead
   */
  @Deprecated
  public VectorQuery withK(int newK) {
    return withNumResults(newK);
  }

  /** Create a copy with modified distance metric */
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

  /** Create a copy with modified pre-filter */
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

    // Add EF runtime if specified (for HNSW)
    if (efRuntime != null) {
      params.put("ef_runtime", efRuntime);
    }

    return params;
  }

  // Getters
  public String getField() {
    return field;
  }

  public float[] getVector() {
    return vector != null ? vector.clone() : null; // Defensive copy
  }

  public int getNumResults() {
    return numResults;
  }

  /**
   * @deprecated Use getNumResults() instead
   */
  @Deprecated
  public int getK() {
    return numResults;
  }

  public VectorField.DistanceMetric getDistanceMetric() {
    return distanceMetric;
  }

  public boolean isReturnDistance() {
    return returnDistance;
  }

  public boolean isReturnScore() {
    return returnScore;
  }

  public String getPreFilter() {
    return preFilter;
  }

  public String getHybridField() {
    return hybridField;
  }

  public String getHybridQuery() {
    return hybridQuery;
  }

  public Integer getEfRuntime() {
    return efRuntime;
  }

  public void setEfRuntime(int efRuntime) {
    this.efRuntime = efRuntime;
  }

  public List<String> getReturnFields() {
    return returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  public Filter getFilter() {
    return filter;
  }

  public void setFilter(Filter filter) {
    this.filter = filter;
  }

  public String getHybridPolicy() {
    return hybridPolicy;
  }

  public void setHybridPolicy(String policy) {
    this.hybridPolicy = policy;
  }

  public Integer getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public boolean isNormalizeVectorDistance() {
    return normalizeVectorDistance;
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
    private String hybridField;
    private String hybridQuery;
    private Integer efRuntime;
    private List<String> returnFields;
    private boolean normalizeVectorDistance = false;

    private static float[] toFloatArray(double[] doubles) {
      if (doubles == null) return null;
      float[] floats = new float[doubles.length];
      for (int i = 0; i < doubles.length; i++) {
        floats[i] = (float) doubles[i];
      }
      return floats;
    }

    public Builder field(String field) {
      this.field = field;
      return this;
    }

    public Builder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null; // Defensive copy
      return this;
    }

    public Builder vector(double[] vector) {
      this.vector = toFloatArray(vector);
      return this;
    }

    public Builder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * @deprecated Use numResults() instead
     */
    @Deprecated
    public Builder k(int k) {
      this.numResults = k;
      return this;
    }

    /**
     * @deprecated Use numResults() instead
     */
    @Deprecated
    public Builder withK(int k) {
      this.numResults = k;
      return this;
    }

    public Builder distanceMetric(VectorField.DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    public Builder withDistanceMetric(VectorField.DistanceMetric metric) {
      this.distanceMetric = metric;
      return this;
    }

    public Builder returnDistance(boolean returnDistance) {
      this.returnDistance = returnDistance;
      return this;
    }

    public Builder withReturnDistance(boolean returnDistance) {
      this.returnDistance = returnDistance;
      return this;
    }

    public Builder returnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    public Builder withReturnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    public Builder preFilter(String preFilter) {
      this.preFilter = preFilter;
      return this;
    }

    public Builder withPreFilter(String filter) {
      this.preFilter = filter;
      return this;
    }

    public Builder hybridField(String hybridField) {
      this.hybridField = hybridField;
      return this;
    }

    public Builder hybridQuery(String hybridQuery) {
      this.hybridQuery = hybridQuery;
      return this;
    }

    public Builder withHybridSearch(String field, String query) {
      this.hybridField = field;
      this.hybridQuery = query;
      return this;
    }

    public Builder efRuntime(Integer efRuntime) {
      this.efRuntime = efRuntime;
      return this;
    }

    public Builder withEfRuntime(int efRuntime) {
      this.efRuntime = efRuntime;
      return this;
    }

    public Builder returnFields(String... fields) {
      this.returnFields = Arrays.asList(fields);
      return this;
    }

    public Builder returnFields(List<String> fields) {
      this.returnFields = fields != null ? new ArrayList<>(fields) : null;
      return this;
    }

    public Builder normalizeVectorDistance(boolean normalize) {
      this.normalizeVectorDistance = normalize;
      return this;
    }

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
          returnFields,
          normalizeVectorDistance);
    }
  }
}
