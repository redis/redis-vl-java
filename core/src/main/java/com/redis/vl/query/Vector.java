package com.redis.vl.query;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

/**
 * Simple object containing the necessary arguments to perform a multi-vector query.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:16-36 (Vector class)
 *
 * <p>Python equivalent:
 *
 * <pre>
 * from redisvl.query import Vector
 *
 * vector = Vector(
 *     vector=[0.1, 0.2, 0.3],
 *     field_name="text_embedding",
 *     dtype="float32",
 *     weight=0.7
 * )
 * </pre>
 *
 * Java equivalent:
 *
 * <pre>
 * Vector vector = Vector.builder()
 *     .vector(new float[]{0.1f, 0.2f, 0.3f})
 *     .fieldName("text_embedding")
 *     .dtype("float32")
 *     .weight(0.7)
 *     .build();
 * </pre>
 */
@Getter
public final class Vector {

  private static final Set<String> VALID_DTYPES =
      new HashSet<>(
          Arrays.asList(
              "BFLOAT16",
              "bfloat16",
              "FLOAT16",
              "float16",
              "FLOAT32",
              "float32",
              "FLOAT64",
              "float64",
              "INT8",
              "int8",
              "UINT8",
              "uint8"));

  private final float[] vector;
  private final String fieldName;
  private final String dtype;
  private final double weight;

  private Vector(Builder builder) {
    // Validate before modifying state
    if (builder.vector == null || builder.vector.length == 0) {
      throw new IllegalArgumentException("Vector cannot be null or empty");
    }
    if (builder.fieldName == null || builder.fieldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name cannot be null or empty");
    }
    if (!VALID_DTYPES.contains(builder.dtype)) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid data type: %s. Supported types are: %s", builder.dtype, VALID_DTYPES));
    }
    if (builder.weight <= 0) {
      throw new IllegalArgumentException("Weight must be positive, got " + builder.weight);
    }

    this.vector = Arrays.copyOf(builder.vector, builder.vector.length);
    this.fieldName = builder.fieldName.trim();
    this.dtype = builder.dtype;
    this.weight = builder.weight;
  }

  /**
   * Create a new Builder for Vector.
   *
   * @return A new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get a copy of the vector.
   *
   * @return Copy of the vector
   */
  public float[] getVector() {
    return Arrays.copyOf(vector, vector.length);
  }

  /** Builder for creating Vector instances. */
  public static class Builder {
    private float[] vector;
    private String fieldName;
    private String dtype = "float32"; // Default from Python
    private double weight = 1.0; // Default from Python

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
     * @param fieldName Name of the vector field
     * @return This builder
     */
    public Builder fieldName(String fieldName) {
      this.fieldName = fieldName;
      return this;
    }

    /**
     * Set the vector data type.
     *
     * <p>Supported types: BFLOAT16, FLOAT16, FLOAT32, FLOAT64, INT8, UINT8 (case-insensitive)
     *
     * @param dtype Vector data type
     * @return This builder
     */
    public Builder dtype(String dtype) {
      this.dtype = dtype;
      return this;
    }

    /**
     * Set the weight for this vector in multi-vector scoring.
     *
     * <p>The final score will be a weighted combination: w_1 * score_1 + w_2 * score_2 + ...
     *
     * @param weight Weight value (must be positive)
     * @return This builder
     */
    public Builder weight(double weight) {
      this.weight = weight;
      return this;
    }

    /**
     * Build the Vector instance.
     *
     * @return Configured Vector
     * @throws IllegalArgumentException if vector or fieldName is null/empty, dtype is invalid, or
     *     weight is non-positive
     */
    public Vector build() {
      return new Vector(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Vector vector1 = (Vector) o;
    return Double.compare(vector1.weight, weight) == 0
        && Arrays.equals(vector, vector1.vector)
        && fieldName.equals(vector1.fieldName)
        && dtype.equals(vector1.dtype);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(vector);
    result = 31 * result + fieldName.hashCode();
    result = 31 * result + dtype.hashCode();
    result = 31 * result + Double.hashCode(weight);
    return result;
  }

  @Override
  public String toString() {
    return String.format(
        "Vector[fieldName=%s, dtype=%s, weight=%.2f, dimensions=%d]",
        fieldName, dtype, weight, vector.length);
  }
}
