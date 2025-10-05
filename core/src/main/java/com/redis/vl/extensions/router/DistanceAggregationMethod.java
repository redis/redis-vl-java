package com.redis.vl.extensions.router;

/**
 * Enumeration for distance aggregation methods. Ported from Python:
 * redisvl/extensions/router/schema.py:50
 */
public enum DistanceAggregationMethod {
  /** Average aggregation method */
  AVG("avg"),
  /** Minimum aggregation method */
  MIN("min"),
  /** Sum aggregation method */
  SUM("sum");

  private final String value;

  DistanceAggregationMethod(String value) {
    this.value = value;
  }

  /**
   * Get the string value of the aggregation method.
   *
   * @return the string value
   */
  public String getValue() {
    return value;
  }

  /**
   * Get the DistanceAggregationMethod from a string value.
   *
   * @param value the string value
   * @return the corresponding DistanceAggregationMethod
   * @throws IllegalArgumentException if the value is unknown
   */
  public static DistanceAggregationMethod fromValue(String value) {
    for (DistanceAggregationMethod method : values()) {
      if (method.value.equalsIgnoreCase(value)) {
        return method;
      }
    }
    throw new IllegalArgumentException("Unknown aggregation method: " + value);
  }
}
