package com.redis.vl.extensions.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a matched route with distance information. Ported from Python:
 * redisvl/extensions/router/schema.py:41
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("javadoc")
public class RouteMatch {
  private String name;
  private Double distance;
}
