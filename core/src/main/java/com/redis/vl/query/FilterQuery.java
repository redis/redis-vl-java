package com.redis.vl.query;

import java.util.List;
import java.util.Map;
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
public class FilterQuery {

  /** Private constructor used by builder. */
  private FilterQuery(FilterQueryBuilder builder) {
    this.filterExpression = builder.filterExpression;
    this.returnFields =
        builder.returnFields != null ? List.copyOf(builder.returnFields) : List.of();
    this.numResults = builder.numResults;
    this.dialect = builder.dialect;
    this.sortBy = builder.sortBy;
    this.inOrder = builder.inOrder;
    this.params = builder.params != null ? Map.copyOf(builder.params) : Map.of();
  }

  /** Create a new builder. */
  public static FilterQueryBuilder builder() {
    return new FilterQueryBuilder();
  }

  /**
   * The filter expression to query with. Python: filter_expression (Optional[Union[str,
   * FilterExpression]]) Defaults to '*' if null.
   */
  private final Filter filterExpression;

  /** The fields to return in results. Python: return_fields (Optional[List[str]]) */
  private final List<String> returnFields;

  /** The number of results to return. Python: num_results (int) - defaults to 10 */
  private final int numResults;

  /** The query dialect (RediSearch version). Python: dialect (int) - defaults to 2 */
  private final int dialect;

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
  private final boolean inOrder;

  /** Additional parameters for the query. Python: params (Optional[Dict[str, Any]]) */
  private final Map<String, Object> params;

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

  // Getters
  public Filter getFilterExpression() {
    return filterExpression;
  }

  public List<String> getReturnFields() {
    return returnFields;
  }

  public int getNumResults() {
    return numResults;
  }

  public int getDialect() {
    return dialect;
  }

  public String getSortBy() {
    return sortBy;
  }

  public boolean isInOrder() {
    return inOrder;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  /** Builder for FilterQuery with defensive copying. */
  public static class FilterQueryBuilder {
    private Filter filterExpression;
    private List<String> returnFields = List.of();
    private int numResults = 10;
    private int dialect = 2;
    private String sortBy;
    private boolean inOrder = false;
    private Map<String, Object> params = Map.of();

    FilterQueryBuilder() {}

    public FilterQueryBuilder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set return fields. Makes a defensive copy.
     *
     * @param returnFields List of field names
     * @return this builder
     */
    public FilterQueryBuilder returnFields(List<String> returnFields) {
      this.returnFields = returnFields != null ? List.copyOf(returnFields) : List.of();
      return this;
    }

    public FilterQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    public FilterQueryBuilder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    public FilterQueryBuilder sortBy(String sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    public FilterQueryBuilder inOrder(boolean inOrder) {
      this.inOrder = inOrder;
      return this;
    }

    /**
     * Set query parameters. Makes a defensive copy.
     *
     * @param params Parameter map
     * @return this builder
     */
    public FilterQueryBuilder params(Map<String, Object> params) {
      this.params = params != null ? Map.copyOf(params) : Map.of();
      return this;
    }

    public FilterQuery build() {
      return new FilterQuery(this);
    }
  }
}
