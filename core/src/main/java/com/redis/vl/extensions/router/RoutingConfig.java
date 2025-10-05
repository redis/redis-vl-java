package com.redis.vl.extensions.router;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for routing behavior. Ported from Python: redisvl/extensions/router/schema.py:61
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class RoutingConfig {
  @Builder.Default private int maxK = 1;

  @Builder.Default
  private DistanceAggregationMethod aggregationMethod = DistanceAggregationMethod.AVG;

  /**
   * Validate the routing configuration.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (maxK <= 0) {
      throw new IllegalArgumentException("maxK must be greater than 0");
    }
  }
}
