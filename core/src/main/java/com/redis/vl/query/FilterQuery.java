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
    this.sortAscending = builder.sortAscending;
    this.inOrder = builder.inOrder;
    this.params = builder.params != null ? Map.copyOf(builder.params) : Map.of();
  }

  /**
   * Create a new builder.
   *
   * @return A new FilterQueryBuilder instance
   */
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
   * Whether to sort in ascending order. Python: query.sort_by(field, asc=True/False) Defaults to
   * true (ascending).
   */
  private final boolean sortAscending;

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
    // Python supports: query.sort_by(field, asc=True/False)
    if (sortBy != null && !sortBy.isEmpty()) {
      query.setSortBy(sortBy, sortAscending);
    }

    // Python: if in_order: self.in_order()
    if (inOrder) {
      query.setInOrder();
    }

    return query;
  }

  // Getters

  /**
   * Get the filter expression.
   *
   * @return The filter expression
   */
  public Filter getFilterExpression() {
    return filterExpression;
  }

  /**
   * Get the fields to return in results.
   *
   * @return List of field names to return
   */
  public List<String> getReturnFields() {
    return returnFields;
  }

  /**
   * Get the number of results to return.
   *
   * @return Number of results
   */
  public int getNumResults() {
    return numResults;
  }

  /**
   * Get the query dialect.
   *
   * @return RediSearch dialect version
   */
  public int getDialect() {
    return dialect;
  }

  /**
   * Get the field to sort by.
   *
   * @return Sort field name, or null if not sorting
   */
  public String getSortBy() {
    return sortBy;
  }

  /**
   * Check if in-order matching is required.
   *
   * @return true if terms must appear in same order as query
   */
  public boolean isInOrder() {
    return inOrder;
  }

  /**
   * Get additional query parameters.
   *
   * @return Map of parameter name to value
   */
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
    private boolean sortAscending = true; // Default to ascending
    private boolean inOrder = false;
    private Map<String, Object> params = Map.of();

    FilterQueryBuilder() {}

    /**
     * Set the filter expression.
     *
     * @param filterExpression The filter to apply
     * @return this builder
     */
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

    /**
     * Set the number of results to return.
     *
     * @param numResults Maximum number of results
     * @return this builder
     */
    public FilterQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the query dialect.
     *
     * @param dialect RediSearch dialect version
     * @return this builder
     */
    public FilterQueryBuilder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    /**
     * Set the field to sort results by (defaults to ascending).
     *
     * <p>Python equivalent: sort_by="price"
     *
     * @param sortBy Field name to sort by
     * @return this builder
     */
    public FilterQueryBuilder sortBy(String sortBy) {
      this.sortBy = sortBy;
      this.sortAscending = true; // Default to ascending
      return this;
    }

    /**
     * Set the field to sort results by with explicit direction.
     *
     * <p>Python equivalent: sort_by=("price", "DESC")
     *
     * @param field Field name to sort by
     * @param direction Sort direction ("ASC" or "DESC", case-insensitive)
     * @return this builder
     * @throws IllegalArgumentException if direction is invalid
     */
    public FilterQueryBuilder sortBy(String field, String direction) {
      List<SortField> parsed = SortSpec.parseSortSpec(field, direction);
      SortField sortField = parsed.get(0);
      this.sortBy = sortField.getFieldName();
      this.sortAscending = sortField.isAscending();
      return this;
    }

    /**
     * Set the field to sort results by using SortField.
     *
     * <p>Python equivalent: sort_by=("rating", "DESC") or using SortField.desc("rating")
     *
     * @param sortField SortField specifying field and direction
     * @return this builder
     * @throws IllegalArgumentException if sortField is null
     */
    public FilterQueryBuilder sortBy(SortField sortField) {
      if (sortField == null) {
        throw new IllegalArgumentException("SortField cannot be null");
      }
      this.sortBy = sortField.getFieldName();
      this.sortAscending = sortField.isAscending();
      return this;
    }

    /**
     * Set the fields to sort results by (supports multiple fields, but only first is used).
     *
     * <p>Python equivalent: sort_by=[("price", "DESC"), ("rating", "ASC"), "stock"]
     *
     * <p>Note: Redis Search only supports single-field sorting. When multiple fields are provided,
     * only the first field is used and a warning is logged.
     *
     * @param sortFields List of SortFields
     * @return this builder
     */
    public FilterQueryBuilder sortBy(List<SortField> sortFields) {
      List<SortField> parsed = SortSpec.parseSortSpec(sortFields);
      if (!parsed.isEmpty()) {
        SortField firstField = parsed.get(0);
        this.sortBy = firstField.getFieldName();
        this.sortAscending = firstField.isAscending();
      } else {
        // Empty list - clear sorting
        this.sortBy = null;
      }
      return this;
    }

    /**
     * Set whether to sort in ascending or descending order. Python: query.sort_by(field,
     * asc=True/False)
     *
     * @param ascending true for ascending, false for descending
     * @return this builder
     */
    public FilterQueryBuilder sortAscending(boolean ascending) {
      this.sortAscending = ascending;
      return this;
    }

    /**
     * Set whether to require in-order term matching.
     *
     * @param inOrder true to require terms in same order as query
     * @return this builder
     */
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

    /**
     * Build the FilterQuery instance.
     *
     * @return A new FilterQuery with the configured parameters
     */
    public FilterQuery build() {
      return new FilterQuery(this);
    }
  }
}
