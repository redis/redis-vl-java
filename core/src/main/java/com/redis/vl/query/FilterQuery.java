package com.redis.vl.query;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import redis.clients.jedis.search.Query;

/**
 * A query for running a filtered search with a filter expression (no vector search).
 *
 * <p>Ported from Python: redisvl/query/query.py:314 (FilterQuery class)
 *
 * <p>Python equivalent:
 *
 * <pre>
 * FilterQuery(
 *     filter_expression=Tag("credit_score") == "high",
 *     return_fields=["user", "age"],
 *     num_results=10,
 *     sort_by="age"
 * )
 * </pre>
 *
 * Java equivalent:
 *
 * <pre>
 * FilterQuery.builder()
 *     .filterExpression(Filter.tag("credit_score", "high"))
 *     .returnFields(List.of("user", "age"))
 *     .numResults(10)
 *     .sortBy("age")
 *     .build()
 * </pre>
 */
@Getter
@Builder
public class FilterQuery {

  /**
   * The filter expression to query with. Python: filter_expression (Optional[Union[str,
   * FilterExpression]]) Defaults to '*' if null.
   */
  private final Filter filterExpression;

  /** The fields to return in results. Python: return_fields (Optional[List[str]]) */
  @Builder.Default private final List<String> returnFields = List.of();

  /** The number of results to return. Python: num_results (int) - defaults to 10 */
  @Builder.Default private final int numResults = 10;

  /** The query dialect (RediSearch version). Python: dialect (int) - defaults to 2 */
  @Builder.Default private final int dialect = 2;

  /**
   * Field to sort results by. Python: sort_by (Optional[SortSpec]) - can be str, Tuple[str, str],
   * or List Note: Redis Search only supports single-field sorting, so only first field is used.
   * Defaults to ascending order.
   */
  private final String sortBy;

  /**
   * Whether to require terms in field to have same order as in query filter. Python: in_order
   * (bool) - defaults to False
   */
  @Builder.Default private final boolean inOrder = false;

  /** Additional parameters for the query. Python: params (Optional[Dict[str, Any]]) */
  @Builder.Default private final Map<String, Object> params = Map.of();

  /**
   * Build Redis Query object from FilterQuery.
   *
   * <p>Python equivalent: _build_query_string() method (line 368-372) Returns the filter expression
   * string or '*' if no filter.
   *
   * @return Jedis Query object
   */
  public Query buildRedisQuery() {
    // Python: if isinstance(self._filter_expression, FilterExpression):
    //             return str(self._filter_expression)
    //         return self._filter_expression
    String filterStr = (filterExpression != null) ? filterExpression.build() : "*";

    // Python: super().__init__("*")
    // Python: self.paging(0, self._num_results).dialect(dialect)
    Query query = new Query(filterStr).limit(0, numResults).dialect(dialect);

    // Python: if return_fields: self.return_fields(*return_fields)
    if (!returnFields.isEmpty()) {
      query.returnFields(returnFields.toArray(String[]::new));
    }

    // Python: if sort_by: self.sort_by(sort_by)
    // Note: Python accepts tuple for DESC, but here we default to ASC
    if (sortBy != null && !sortBy.isEmpty()) {
      query.setSortBy(sortBy, true); // true = ascending
    }

    // Python: if in_order: self.in_order()
    if (inOrder) {
      query.setInOrder();
    }

    return query;
  }
}
