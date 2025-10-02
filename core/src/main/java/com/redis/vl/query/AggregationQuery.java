package com.redis.vl.query;

import java.util.Map;
import redis.clients.jedis.search.aggr.AggregationBuilder;

/**
 * Base class for aggregation queries used to create aggregation queries for Redis.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:14
 *
 * <p>Python equivalent:
 *
 * <pre>
 * class AggregationQuery(AggregateRequest):
 *     """
 *     Base class for aggregation queries used to create aggregation queries for Redis.
 *     """
 *
 *     def __init__(self, query_string):
 *         super().__init__(query_string)
 * </pre>
 *
 * <p>This is a base class for queries that use Redis aggregation capabilities. Aggregation queries
 * can perform complex data analysis operations like grouping, filtering, sorting, and applying
 * reducer functions.
 *
 * @see HybridQuery
 * @since 0.1.0
 */
public abstract class AggregationQuery {

  /**
   * Build the Redis AggregationBuilder for this query.
   *
   * @return the Jedis AggregationBuilder configured for this query
   */
  public abstract AggregationBuilder buildRedisAggregation();

  /**
   * Build the base query string for the aggregation.
   *
   * @return the query string
   */
  public abstract String buildQueryString();

  /**
   * Get the parameters for the aggregation query.
   *
   * <p>Used for parameterized queries (e.g., vector parameter in HybridQuery).
   *
   * @return a map of parameter names to values
   */
  public abstract Map<String, Object> getParams();
}
