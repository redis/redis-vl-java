package com.redis.vl.query;

/** Query to count documents matching a filter */
public class CountQuery {

  private final String filterString;
  private final Filter filter;

  /**
   * Create a count query with a filter string.
   *
   * @param filterString The filter string to use
   */
  public CountQuery(String filterString) {
    this.filterString = filterString;
    this.filter = null;
  }

  /**
   * Create a count query with a Filter object.
   *
   * @param filter The filter to use
   */
  public CountQuery(Filter filter) {
    this.filterString = null;
    this.filter = filter;
  }

  /**
   * Get the filter string for this query.
   *
   * @return The filter string, or "*" if no filter is specified
   */
  public String getFilterString() {
    if (filterString != null) {
      return filterString;
    }
    if (filter != null) {
      return filter.build();
    }
    return "*";
  }

  /**
   * Get the Filter object for this query.
   *
   * @return The Filter object, or null if a string filter was used
   */
  public Filter getFilter() {
    return filter;
  }

  @Override
  public String toString() {
    return "CountQuery{filter='" + getFilterString() + "'}";
  }
}
