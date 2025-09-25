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
  }

  public static Builder builder() {
    return new Builder();
  }

  public float[] getVector() {
    return vector != null ? Arrays.copyOf(vector, vector.length) : null;
  }

  public String getField() {
    return field;
  }

  public List<String> getReturnFields() {
    return returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  public double getDistanceThreshold() {
    return distanceThreshold;
  }

  public void setDistanceThreshold(double distanceThreshold) {
    if (normalizeVectorDistance && distanceThreshold > 1.0) {
      throw new IllegalArgumentException(
          "Distance threshold must be <= 1.0 when normalizing vector distance");
    }
    this.distanceThreshold = distanceThreshold;
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

  public boolean isReturnScore() {
    return returnScore;
  }

  public boolean isNormalizeVectorDistance() {
    return normalizeVectorDistance;
  }

  public Double getEpsilon() {
    return epsilon;
  }

  public void setEpsilon(double epsilon) {
    this.epsilon = epsilon;
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

  public static class Builder {
    private float[] vector;
    private String field;
    private List<String> returnFields;
    private double distanceThreshold = 0.2;
    private int numResults = 10;
    private boolean returnScore = false;
    private boolean normalizeVectorDistance = false;
    private Double epsilon;

    public Builder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null;
      return this;
    }

    public Builder field(String field) {
      this.field = field;
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

    public Builder distanceThreshold(double threshold) {
      this.distanceThreshold = threshold;
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

    public Builder returnScore(boolean returnScore) {
      this.returnScore = returnScore;
      return this;
    }

    public Builder normalizeVectorDistance(boolean normalize) {
      this.normalizeVectorDistance = normalize;
      return this;
    }

    public Builder epsilon(double epsilon) {
      this.epsilon = epsilon;
      return this;
    }

    public VectorRangeQuery build() {
      return new VectorRangeQuery(this);
    }
  }
}
